package components

import components.RecordingsListPane.Companion.setFocusMnemonic
import core.MainApplication
import core.Session
import dialogs.LoadingDialog
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox

/**
 * This class is a pane that has the different buttons that the user can interact with
 * @author Kacper Lubisz
 */
internal class ControlPane(private val application: MainApplication, scene: Scene, private val session: Session) : BorderPane() {

    private val exitButton = Button(EXIT_BUTTON_TEXT)

    private val recordButton = Button(RECORD_TEXT)
    private val playbackButton = Button(PLAY_TEXT)
    private val cutButton = Button(SCISSORS_TEXT)

    private val muteButton = Button(UN_MUTED_TEXT)

    init {

        session.addOnUpdate {
            onStateUpdate(session.state)
        }
        recordButton.setFocusMnemonic(RECORD_MNEMONIC, scene)
        recordButton.minWidth = BUTTON_WIDTH
        recordButton.isFocusTraversable = false
        // make it so that these buttons can't be focused means that the accelerators in EditPane never stop working
        recordButton.setOnAction {
            if (session.state == Session.SessionState.EDIT_SAFE && session.record()) {
                onStateUpdate(Session.SessionState.GATHERING)
            } else if (session.state == Session.SessionState.GATHERING && session.pauseRecording()) {
                onStateUpdate(Session.SessionState.EDIT_SAFE)
            }
        }

        playbackButton.setFocusMnemonic(PLAY_MNEMONIC, scene)
        playbackButton.minWidth = BUTTON_WIDTH
        playbackButton.isFocusTraversable = false
        playbackButton.setOnAction {
            if (session.state == Session.SessionState.EDIT_SAFE && session.recording.length != 0.0 && session.playback()) {
                // session.playback doesn't get called unless the state is safe
                // under the condition that it is safe the rest of the else if fails
                onStateUpdate(Session.SessionState.PLAYING_BACK)
            } else if (session.state == Session.SessionState.PLAYING_BACK && session.pausePlayback()) {
                onStateUpdate(Session.SessionState.EDIT_SAFE)
            }

        }

        cutButton.setFocusMnemonic(CUT_MNEMONIC, scene)
        cutButton.isDisable = true
        cutButton.minWidth = BUTTON_WIDTH
        cutButton.isFocusTraversable = false
        cutButton.setOnAction {
            if (session.state == Session.SessionState.EDIT_SAFE && session.recording.cut(session.correctedStepCursor)) {
                session.stepCursor = session.correctedStepCursor
                session.onEdited()
            }
        }

        muteButton.setFocusMnemonic(MUTE_MNEMONIC, scene)
        muteButton.minWidth = BUTTON_WIDTH
        muteButton.isFocusTraversable = false
        muteButton.setOnAction {
            val isMuted = session.toggleMute()
            if (isMuted) {
                muteButton.text = MUTED_TEXT
            } else {
                muteButton.text = UN_MUTED_TEXT
            }
        }

        exitButton.setFocusMnemonic(EXIT_MNEMONIC, scene)
        exitButton.minWidth = BUTTON_WIDTH
        exitButton.isDisable = true
        exitButton.isFocusTraversable = false
        exitButton.setOnAction {

            when {
                session.state == Session.SessionState.EDIT_SAFE && session.isEdited -> {

                    val result = RecordingEditPane.showSaveDialog()

                    if (result.get().buttonData == ButtonBar.ButtonData.YES) {

                        val dialog = LoadingDialog("Saving to file", "Saving")
                        // show a saving to file progress bar (indefinite)
                        Platform.runLater {
                            session.recording.save()
                            dialog.dispose()
                            application.pop()
                        }

                    } else if (result.get().buttonData == ButtonBar.ButtonData.NO) application.pop()

                }
                session.state == Session.SessionState.GATHERING -> session.pauseRecording()
                session.state == Session.SessionState.PLAYING_BACK -> session.pausePlayback()
                else -> application.pop()
            }

        }


        val centrePanel = HBox()
        centrePanel.alignment = Pos.CENTER
        centrePanel.spacing = 5.0
        centrePanel.children.addAll(
                recordButton,
                playbackButton,
                cutButton
        )

        padding = Insets(10.0)
        left = exitButton
        center = centrePanel
        right = muteButton

        onStateUpdate(session.state)

    }

    /**
     * When the state of the session changes and the buttons need to be updated
     */
    private fun onStateUpdate(state: Session.SessionState) {

        Platform.runLater {

            recordButton.text = if (state == Session.SessionState.EDIT_SAFE) RECORD_TEXT else STOP_TEXT
            playbackButton.text = if (state == Session.SessionState.EDIT_SAFE) PLAY_TEXT else PAUSE_TEXT

            recordButton.isDisable = !(state == Session.SessionState.EDIT_SAFE || state == Session.SessionState.GATHERING)
            playbackButton.isDisable = !(state == Session.SessionState.EDIT_SAFE || state == Session.SessionState.PLAYING_BACK)

            cutButton.isDisable = state != Session.SessionState.EDIT_SAFE
            muteButton.isDisable = false

            exitButton.isDisable = state != Session.SessionState.EDIT_SAFE

        }

    }

    companion object {

        private const val PLAY_TEXT = "▶"
        private const val RECORD_TEXT = "\u23FA"
        private const val PAUSE_TEXT = "\u23F8"
        private const val STOP_TEXT = "\u23F9"
        private const val SCISSORS_TEXT = "\u2702"
        private const val EXIT_BUTTON_TEXT = "Save & Exit"

        private const val MUTED_TEXT = "🔇"
        private const val UN_MUTED_TEXT = "\uD83D\uDD0A"

        private const val MUTE_MNEMONIC = "M"
        private const val RECORD_MNEMONIC = "R"
        private const val PLAY_MNEMONIC = "P"
        private const val CUT_MNEMONIC = "C"
        private const val EXIT_MNEMONIC = "S"

        private const val BUTTON_WIDTH = 40.0

    }
}