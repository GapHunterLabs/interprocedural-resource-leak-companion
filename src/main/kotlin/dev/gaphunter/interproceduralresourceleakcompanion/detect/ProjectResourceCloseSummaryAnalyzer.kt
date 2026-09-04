package dev.gaphunter.interproceduralresourceleakcompanion.detect

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Whole-project, SCC-ordered fixed-point computation of each method's
 * own "guaranteed parameter close" summary -- for every resource-typed
 * parameter, whether [ResourceStateEngine.propagate] proves it's
 * closed on EVERY path through that method's own body (seeded from
 * its parameters, not local declarations), following calls into OTHER
 * project methods whose OWN summaries (recursively) already prove the
 * same thing.
 *
 * Same Tarjan-SCC-then-fixed-point structure as
 * `log-injection-companion`'s `ProjectLogTaintAnalyzer`, applied to a
 * genuinely different lattice (per-parameter open/closed SETS, not a
 * boolean-reachability taint set) -- this is the interprocedural half
 * of this plugin's combination; [ResourceStateEngine] is the
 * path-sensitive half.
 *
 * **Cancellable, per catalog-wide precedent:** this computation runs
 * inside a `LocalInspectionTool`'s read action, but a pure in-memory
 * fixed-point loop is not automatically interruptible -- a large real
 * project could otherwise block the read action uncancellably while
 * the user keeps typing. [ProgressManager.checkCanceled] is called
 * once per file during the initial scan and once per outer
 * fixed-point iteration per SCC (same discipline as
 * `interface-resource-close-divergence-companion`'s own analyzer,
 * retrofitted here 2026-09-03 after a catalog-wide review found it
 * missing).
 */
object ProjectResourceCloseSummaryAnalyzer {

    const val MAX_METHODS = 3000
    private const val MAX_FILE_LENGTH = 500_000

    private val CACHE_KEY: Key<CachedValue<Map<String, Set<Int>>>> = Key.create("interproceduralResourceLeakCompanion.summaries")

    fun summariesFor(project: Project): Map<String, Set<Int>> {
        return CachedValuesManager.getManager(project).getCachedValue(
            project,
            CACHE_KEY,
            { CachedValueProvider.Result.create(computeSummaries(project), PsiModificationTracker.MODIFICATION_COUNT) },
            false,
        )
    }

    private fun computeSummaries(project: Project): Map<String, Set<Int>> {
        val scope = GlobalSearchScope.projectScope(project)
        val files = FilenameIndex.getAllFilesByExt(project, "java", scope)
        val psiManager = PsiManager.getInstance(project)
        val javaFiles = files.mapNotNull { psiManager.findFile(it) as? PsiJavaFile }.filter { it.text.length <= MAX_FILE_LENGTH }

        val allMethods = mutableListOf<PsiMethod>()
        for (psiFile in javaFiles) {
            ProgressManager.checkCanceled()
            psiFile.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethod(method: PsiMethod) {
                    super.visitMethod(method)
                    if (method.body != null && method.parameterList.parameters.any { ResourceStateEngine.isResourceType(it.type) }) {
                        allMethods += method
                    }
                }
            })
        }
        if (allMethods.size > MAX_METHODS) return emptyMap()

        val methodSet = allMethods.toHashSet()
        val graph: Map<PsiMethod, List<PsiMethod>> = allMethods.associateWith { method -> calleesOf(method, methodSet) }
        val sccsCalleesFirst = TarjanSccComputer(graph).compute()

        val summaries = HashMap<PsiMethod, Set<Int>>()
        for (scc in sccsCalleesFirst) {
            var changed = true
            while (changed) {
                ProgressManager.checkCanceled()
                changed = false
                for (method in scc) {
                    val previous = summaries[method]
                    val recomputed = summaryForMethod(method, summaries)
                    if (recomputed != previous) {
                        summaries[method] = recomputed
                        changed = true
                    }
                }
            }
        }

        return summaries.entries.associate { (method, summary) -> MethodKey.of(method) to summary }
    }

    private fun calleesOf(method: PsiMethod, methodSet: Set<PsiMethod>): List<PsiMethod> {
        val body = method.body ?: return emptyList()
        val callees = mutableListOf<PsiMethod>()
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                super.visitMethodCallExpression(call)
                val resolved = call.resolveMethod() ?: return
                if (resolved in methodSet) callees += resolved
            }
        })
        return callees
    }

    private fun summaryForMethod(method: PsiMethod, summaries: Map<PsiMethod, Set<Int>>): Set<Int> {
        val body = method.body ?: return emptySet()
        val parameters = method.parameterList.parameters

        val initialState = parameters
            .filter { ResourceStateEngine.isResourceType(it.type) }
            .associate { it.name to setOf(false) }

        val finalState = ResourceStateEngine.propagate(body, initialState) { callee -> summaries[callee] }

        return parameters.indices
            .filter { index -> ResourceStateEngine.isResourceType(parameters[index].type) && finalState[parameters[index].name] == setOf(true) }
            .toSet()
    }
}
