package components

import core.AppInstance
import core.AppInstance.ApplicationPane
import core.Recording
import core.Session
import core.Tuning
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.io.FileInputStream
import javax.swing.*
import javax.swing.border.Border
import kotlin.math.roundToInt


class RecordingsListPane : ApplicationPane() {

    private val recordings: MutableList<Recording.PossibleRecording> = mutableListOf()
    private lateinit var recordingList: JList<Recording.PossibleRecording>
    private val repaintThread = RepaintThread(this)
    private lateinit var dataModel: DefaultListModel<Recording.PossibleRecording>

    override fun onCreate() {

        val buttonPanel = JPanel()
        val newButton = JButton("New Recording")
        val editButton = JButton("Edit")
        val deleteButton = JButton("Delete")
        editButton.isEnabled = false
        deleteButton.isEnabled = false

        buttonPanel.add(newButton)
        buttonPanel.add(editButton)
        buttonPanel.add(deleteButton)
        buttonPanel.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)

        val recentRecordingPanel = JPanel(BorderLayout())

        dataModel = DefaultListModel()
        recordingList = JList(dataModel)
        recordingList.setCellRenderer { _, value, _, isSelected, _ ->
            ListElement(value, isSelected)
        }
        recordingList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) { // double click
                    val possibleRecording = recordings[recordingList.selectedIndex]
                    val session = Session(Recording.deserialize(FileInputStream(possibleRecording.file)))
                    AppInstance.push(RecordingEditPane(session))
                }
            }
        })
        recordingList.addListSelectionListener {
            deleteButton.isEnabled = !recordingList.selectedIndices.isEmpty()
            editButton.isEnabled = recordingList.selectedIndices.size == 1
        }
        newButton.addActionListener {
            NewRecordingDialog(recordings)
        }
        deleteButton.addActionListener {
            val choice = JOptionPane.showOptionDialog(AppInstance,
                    "Are you sure you want to delete recording${if (recordingList.selectedIndices.size == 1) "" else "s"} \n${recordingList.selectedIndices.map {
                        recordings[it].metaData.name
                    }.reduce { acc, s -> "$acc, ${if (acc.length - acc.lastIndexOf("\n") >= 30) "\n" else ""}$s" }}?",
                    "Delete Confirmation",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    listOf("No", "Delete").toTypedArray(),
                    0)

            if (choice == 1) { // delete pressed

                recordingList.selectedIndices.sortedDescending().forEach {
                    recordings[it].file.delete()
                    recordings.removeAt(it)
                    dataModel.removeElementAt(it)
                }

            }
        }
        editButton.addActionListener {
            val possibleRecording = recordings[recordingList.selectedIndex]
            val session = Session(Recording.deserialize(FileInputStream(possibleRecording.file)))
            AppInstance.push(RecordingEditPane(session))
        }

        recentRecordingPanel.add(JLabel("Recent Recordings:"), BorderLayout.NORTH)
        recentRecordingPanel.add(JScrollPane(recordingList), BorderLayout.CENTER)

        layout = BorderLayout()
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        add(recentRecordingPanel, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.NORTH)

    }

    override fun onPause() {
        repaintThread.isPaused = true
    }

    override fun onResume() {
        repaintThread.isPaused = false

        recordings.clear()
        recordings.addAll(Recording.findPossibleRecordings(File(Recording.DEFAULT_PATH)))
        recordings.sortByDescending { it.metaData.lastEdited }
        dataModel.clear()
        recordings.forEach { dataModel.addElement(it) }

    }

    override fun onDestroy() {
    }

    private class ListElement(possibleRecording: Recording.PossibleRecording, selected: Boolean) : JPanel() {

        init {

            layout = BorderLayout()
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            val leftLabel = JLabel(possibleRecording.metaData.name)
            leftLabel.font = leftLabel.font.deriveFont(20f)

            val rightPanel = JPanel(BorderLayout())
            rightPanel.add(JLabel(possibleRecording.metaData.lastEdited.toRelativeTime()), BorderLayout.NORTH)
            rightPanel.add(JLabel(possibleRecording.metaData.length.toLength()), BorderLayout.SOUTH)

            add(leftLabel, BorderLayout.WEST)
            add(rightPanel, BorderLayout.EAST)
            rightPanel.border = BorderFactory.createEmptyBorder(0, 10, 0, 0)

            background = if (selected) SELECTED_COLOUR else background
            rightPanel.background = background

        }

        companion object {

            val SELECTED_COLOUR = Color(186, 212, 255)

        }

    }

    private class RepaintThread(val pane: RecordingsListPane) : Thread("Repaint Thread") {
        init {
            start()
        }

        var isPaused = false
        override fun run() {
            while (!isInterrupted) {
                if (isPaused)
                    while (isPaused) {
                        onSpinWait()
                    }
                else {
                    pane.repaint()
                    sleep(100)
                }
            }
        }
    }

    private class NewRecordingDialog(recordings: MutableList<Recording.PossibleRecording>)
        : JDialog(AppInstance, "New Recording", ModalityType.APPLICATION_MODAL) {

        var customTuning: Tuning? = null
        var tuningComboBox: JComboBox<String>

        init {
            layout = GridBagLayout()

            val constraint = GridBagConstraints()

            val nameField = JTextField()
            val tunings = Tuning.defaultTunings.map { it.name }.toMutableList()
            tunings.add("Custom Tuning")
            tuningComboBox = JComboBox(tunings.toTypedArray())
            tuningComboBox.addActionListener {
                if (tuningComboBox.selectedIndex == Tuning.defaultTunings.size) {
                    tuningComboBox.transferFocus()
                    TuningMakerDialog(this@NewRecordingDialog)
                }
            }
            val loadButton = JButton("Load File")
            loadButton.isEnabled = false
            val recordButton = JButton("Record")

            constraint.anchor = GridBagConstraints.EAST
            constraint.fill = GridBagConstraints.NONE
            constraint.gridy = 0
            constraint.gridx = 0
            constraint.insets = Insets(10, 10, 5, 5)
            add(JLabel("Name:"), constraint)

            constraint.anchor = GridBagConstraints.CENTER
            constraint.fill = GridBagConstraints.HORIZONTAL
            constraint.gridx = 1
            constraint.insets = Insets(10, 5, 5, 10)
            add(nameField, constraint)
            nameField.addActionListener {
                recordButton.doClick()
            }

            constraint.anchor = GridBagConstraints.EAST
            constraint.gridy = 1
            constraint.fill = GridBagConstraints.NONE
            constraint.gridx = 0
            constraint.insets = Insets(5, 10, 5, 5)
            add(JLabel("Tuning:"), constraint)

            constraint.anchor = GridBagConstraints.CENTER
            constraint.fill = GridBagConstraints.HORIZONTAL
            constraint.gridx = 1
            constraint.insets = Insets(5, 5, 5, 10)
            add(tuningComboBox, constraint)

            val buttons = JPanel()
            buttons.add(loadButton, constraint)
            buttons.add(recordButton, constraint)

            loadButton.addActionListener {

            }
            recordButton.addActionListener {

                val name = if (nameField.text.isEmpty()) "Nameless" else nameField.text
                val regex = "$name(\\d| )*".toRegex()
                println(recordings)
                val sameNames = recordings.map { it.metaData.name }
                        .filter { regex.matches(it) }
                        .map { if (it.length == name.length) 0 else it.substring(name.length).trim().toInt() }
                        .onEach { println(it) }
                        .max()
                val newName = name + if (sameNames == null) "" else " ${sameNames + 1}"

                val tuning = if (tuningComboBox.selectedIndex == Tuning.defaultTunings.size)
                    customTuning ?: Tuning.defaultTunings[0] // this null case shouldn't happen
                else
                    Tuning.defaultTunings[tuningComboBox.selectedIndex]

                val session = Session(Recording(tuning, newName))
                AppInstance.push(RecordingEditPane(session))
                dispose()

            }

            constraint.gridx = 0
            constraint.gridy = 2
            constraint.gridwidth = 2
            constraint.insets = Insets(0, 5, 5, 5)
            add(buttons, constraint)

            pack()
            setLocationRelativeTo(AppInstance)
            isVisible = true
        }

        fun refresh(tuning: Tuning) {

            customTuning = tuning
            tuningComboBox.removeItemAt(tuningComboBox.itemCount - 1)
            tuningComboBox.addItem(tuning.name)
            tuningComboBox.selectedIndex = tuningComboBox.itemCount - 1

            pack()
            setLocationRelativeTo(AppInstance)

        }

    }

    private class TuningMakerDialog(val previous: NewRecordingDialog) : JDialog(previous, "Tuning Editor", ModalityType.APPLICATION_MODAL) {
        var tuning: Tuning = Tuning("NAME OF THE THING I HAVE", "e2", maxFret = 3)

        init {
            val constraint = GridBagConstraints()

            layout = GridBagLayout()

            val topPanel = JPanel(GridBagLayout())

            constraint.weightx = 0.0
            constraint.anchor = GridBagConstraints.EAST
            constraint.fill = GridBagConstraints.NONE
            constraint.gridx = 0
            constraint.gridy = 0
            topPanel.add(JLabel("Name:"), constraint)

            constraint.weightx = 1.0
            constraint.anchor = GridBagConstraints.CENTER
            constraint.fill = GridBagConstraints.HORIZONTAL
            constraint.gridx = 1
            constraint.gridy = 0
            val nameField = JTextField()
            topPanel.add(nameField, constraint)

            constraint.weightx = 0.0
            constraint.anchor = GridBagConstraints.EAST
            constraint.fill = GridBagConstraints.NONE
            constraint.gridx = 0
            constraint.gridy = 1
            topPanel.add(JLabel("Capo:"), constraint)

            constraint.weightx = 1.0
            constraint.anchor = GridBagConstraints.CENTER
            constraint.fill = GridBagConstraints.HORIZONTAL
            constraint.gridx = 1
            constraint.gridy = 1
            val capoSinner = JSpinner(SpinnerNumberModel(0, 0, 20, 1))
            topPanel.add(capoSinner, constraint)

            constraint.weightx = 0.0
            constraint.anchor = GridBagConstraints.EAST
            constraint.fill = GridBagConstraints.NONE
            constraint.gridx = 0
            constraint.gridy = 2
            constraint.gridwidth = 1
            topPanel.add(JLabel("Max Fret:"), constraint)
            val maxFretSinner = JSpinner(SpinnerNumberModel(0, 0, 20, 1))

            constraint.weightx = 1.0
            constraint.anchor = GridBagConstraints.CENTER
            constraint.fill = GridBagConstraints.HORIZONTAL
            constraint.gridx = 1
            constraint.gridy = 2
            constraint.gridwidth = 2
            topPanel.add(maxFretSinner, constraint)


            val dataModel = DefaultListModel<String>()
            val stringList = JList<String>(dataModel)
//            recordingList.setCellRenderer { _, value, _, isSelected, _ ->
//                ListElement(value, isSelected)
//            }

            val bottomPanel = JPanel(GridBagLayout())

            constraint.weightx = 1.0
            constraint.gridx = 0
            constraint.gridy = 0
            constraint.gridwidth = 2
            constraint.fill = GridBagConstraints.HORIZONTAL
            val newNoteField = JTextField()
            bottomPanel.add(newNoteField, constraint)

            constraint.weightx = 0.0
            constraint.gridx = 2
            constraint.gridy = 0
            constraint.gridwidth = 1
            val addButton = JButton("Add")
            bottomPanel.add(addButton, constraint)

            constraint.gridx = 3
            constraint.gridy = 0
            val deleteButton = JButton("Delete")
            bottomPanel.add(deleteButton, constraint)

            constraint.gridx = 0
            constraint.gridy = 1
            constraint.gridwidth = 2
            val upButton = JButton("Up")
            bottomPanel.add(upButton, constraint)

            constraint.gridx = 2
            constraint.gridy = 1
            constraint.gridwidth = 2
            val downButton = JButton("Down")
            bottomPanel.add(downButton, constraint)

            constraint.gridx = 0
            constraint.gridy = 2
            constraint.gridwidth = 4
            val createButton = JButton("Create")
            createButton.addActionListener {
                previous.refresh(tuning)
                dispose()
            }
            bottomPanel.add(createButton, constraint)

            constraint.gridx = 0
            constraint.gridy = 0
            constraint.gridwidth = 1
            constraint.anchor = GridBagConstraints.CENTER
            constraint.fill = GridBagConstraints.HORIZONTAL
            constraint.insets = Insets(10, 10, 10, 10)
            add(topPanel, constraint)

            constraint.gridx = 0
            constraint.gridy = 1
            constraint.anchor = GridBagConstraints.CENTER
            constraint.fill = GridBagConstraints.HORIZONTAL
            constraint.insets = Insets(0, 10, 0, 10)
            add(JScrollPane(stringList), constraint)

            constraint.gridx = 0
            constraint.gridy = 2
            constraint.anchor = GridBagConstraints.CENTER
            constraint.fill = GridBagConstraints.HORIZONTAL
            constraint.insets = Insets(10, 10, 10, 10)
            add(bottomPanel, constraint)

            pack()
            setLocationRelativeTo(previous)
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            isVisible = true
        }

    }

}

private fun Double.toLength(): String {
    val minutes = this / 60
    return when {
        this == 0.0 -> "-"
        this < 60 -> "${this.roundToInt()}s"
        minutes < 5 -> "${minutes.roundToInt()}m"
        else -> "${minutes.roundToInt()}m ${this.rem(60).roundToInt()}s"
    }
}

private fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val millis = now - this
    val seconds = millis / 1000.0
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7

    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "${seconds.roundToInt()} seconds ago"
        minutes < 2 -> "1 minute ago"
        minutes < 60 -> "${minutes.roundToInt()} minutes ago"
        hours < 2 -> "1 hour ago"
        hours < 24 -> "${hours.roundToInt()} hours ago"
        days < 2 -> "1 day ago"
        days < 7 -> "${days.roundToInt()} days ago"
        weeks < 2 -> "1 week ago"
        weeks < 52 -> "${weeks.roundToInt()} weeks ago"
        else -> "over a year ago"
    }
}