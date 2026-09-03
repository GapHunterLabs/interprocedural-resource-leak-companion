package dev.gaphunter.interproceduralresourceleakcompanion.model

import com.intellij.psi.PsiElement

/** A confirmed interprocedural leak: [variableName] was passed to a project method that does NOT guarantee closing it on every path, and [variableName] is still not guaranteed closed by the end of the CALLER's own method either. [anchor] is the call site where it was delegated. */
data class ResourceLeakHit(val anchor: PsiElement, val variableName: String)
