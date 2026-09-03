package dev.gaphunter.interproceduralresourceleakcompanion.detect

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import dev.gaphunter.interproceduralresourceleakcompanion.model.ResourceLeakHit

/**
 * Finds a local resource variable passed as an argument to a resolved
 * project method whose own summary ([ProjectResourceCloseSummaryAnalyzer])
 * does NOT guarantee closing that parameter, where the variable is
 * ALSO not guaranteed closed by the end of the CALLER's own method
 * ([ResourceStateEngine.propagate], seeded from local declarations
 * this time) -- a real interprocedural leak: ownership was delegated
 * to a helper that doesn't take care of it, and the caller never did
 * either.
 *
 * **Deliberately gated on a real delegation attempt** (`passSites`,
 * below) rather than flagging every local resource variable that ends
 * up unclosed -- the platform's own bundled "JDBC resource opened but
 * not safely closed" inspection already covers the simple
 * never-touched-at-all case; this plugin only adds the genuinely new,
 * interprocedural angle.
 */
object InterproceduralResourceLeakFinder {

    fun findAll(file: PsiFile): List<ResourceLeakHit> {
        val hits = mutableListOf<ResourceLeakHit>()
        val project = file.project
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                val body = method.body ?: return
                hits += hitsInMethod(body, project)
            }
        })
        return hits
    }

    private fun hitsInMethod(body: PsiCodeBlock, project: Project): List<ResourceLeakHit> {
        val summaries = ProjectResourceCloseSummaryAnalyzer.summariesFor(project)

        // The FIRST call site, per variable, that delegates it to a project method
        // whose summary does NOT prove that parameter gets closed.
        val passSites = LinkedHashMap<String, PsiElement>()
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                super.visitMethodCallExpression(call)
                val callee = call.resolveMethod() ?: return
                val summary = summaries[MethodKey.of(callee)] ?: return
                for ((index, argument) in call.argumentList.expressions.withIndex()) {
                    val reference = argument as? PsiReferenceExpression ?: continue
                    if (reference.qualifierExpression != null) continue
                    val name = reference.referenceName ?: continue
                    if (index !in summary) {
                        passSites.putIfAbsent(name, call.methodExpression.referenceNameElement ?: call.methodExpression)
                    }
                }
            }
        })
        if (passSites.isEmpty()) return emptyList()

        val finalState = ResourceStateEngine.propagate(body, emptyMap()) { callee -> summaries[MethodKey.of(callee)] }

        val hits = mutableListOf<ResourceLeakHit>()
        for ((name, anchor) in passSites) {
            val states = finalState[name] ?: continue
            if (false in states) hits += ResourceLeakHit(anchor, name)
        }
        return hits
    }
}
