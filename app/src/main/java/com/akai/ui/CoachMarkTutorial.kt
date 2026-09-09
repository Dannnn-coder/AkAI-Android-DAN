package com.akai.ui

import android.app.Activity
import android.view.View

/**
 * Fully-MODAL spotlight walkthrough built on the shared [ContextualHelpOverlay]:
 *
 *  - The overlay starts directly on step 1 with the spotlight already on the
 *    component and the caption centered (no jumping; the caption never moves
 *    between the middle steps).
 *  - Only Skip / Back / Next ("Got It!" on the last step) drive the walkthrough;
 *    every other touch is swallowed, so a highlighted component NEVER performs its
 *    real action.
 *  - The overlay owns the stable centered caption and animates it UP (ease-in/out)
 *    only for the second-to-last step (the conversation) and back to center on the
 *    last step.
 */
class CoachMarkTutorial(
    private val activity: Activity,
    private val steps: List<Step>,
    private val onFinished: () -> Unit
) {
    data class Step(val target: View, val title: String, val description: String)

    private var overlay: ContextualHelpOverlay? = null

    fun start() {
        if (steps.isEmpty()) {
            onFinished()
            return
        }
        val contextTargets = steps.map {
            ContextualHelpOverlay.HelpTarget(it.target, it.title, it.description)
        }
        val tutor = ContextualHelpOverlay(activity, contextTargets, tutorial = true) { onFinished() }
        overlay = tutor
        tutor.start()
        tutor.goToStep(0)
    }
}