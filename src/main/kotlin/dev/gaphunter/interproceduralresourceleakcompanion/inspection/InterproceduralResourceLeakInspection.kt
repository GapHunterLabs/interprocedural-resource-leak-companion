package dev.gaphunter.interproceduralresourceleakcompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import dev.gaphunter.interproceduralresourceleakcompanion.detect.InterproceduralResourceLeakFinder
import dev.gaphunter.interproceduralresourceleakcompanion.model.ResourceLeakHit
import dev.gaphunter.interproceduralresourceleakcompanion.review.ReviewPrompt

/** Flags a resource delegated to a helper method that doesn't guarantee closing it, and never closed by the caller either -- see [InterproceduralResourceLeakFinder]. */
class InterproceduralResourceLeakInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null

        val hits = InterproceduralResourceLeakFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                messageFor(hit),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber:${hit.variableName}")
            }
        }

        return problems.toTypedArray()
    }

    private fun messageFor(hit: ResourceLeakHit): String =
        "'${hit.variableName}' is passed here to a method that doesn't guarantee closing it on every path, and it's never " +
            "closed in this method either (CWE-772) -- a real interprocedural resource leak"
}
