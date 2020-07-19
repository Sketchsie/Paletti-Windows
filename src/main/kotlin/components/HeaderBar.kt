package components

import javafx.beans.NamedArg
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.stage.Screen
import javafx.stage.Stage

class HeaderBar(@NamedArg("stage") private val stage: Stage) : HBox() {
    @FXML
    private lateinit var maximizeButton: Button

    val isMaximized = SimpleBooleanProperty(false)
    private var originalWidth = 0.0
    private var originalHeight = 0.0
    private var originalLeft = 0.0
    private var originalTop = 0.0

    init {
        FXMLLoader(javaClass.getResource("HeaderBar.fxml")).apply {
            setRoot(this@HeaderBar)
            setController(this@HeaderBar)
            load()
        }
    }

    fun onClose(event: ActionEvent) {
        stage.close()
        event.consume()
    }

    fun onMinimize(event: ActionEvent) {
        stage.isIconified = true
        event.consume()
    }

    fun onMaximize(event: ActionEvent) {
        isMaximized.value = !isMaximized.value
        if (isMaximized.value) {
            stage.scene.root.styleClass.remove("stage-shadow")
            maximizeButton.text = "\uE923"
            originalWidth = stage.width
            originalHeight = stage.height
            originalLeft = stage.x
            originalTop = stage.y
            Screen.getPrimary().visualBounds.apply {
                stage.width = width
                stage.height = height
                stage.x = minX
                stage.y = minY
            }
        } else {
            stage.scene.root.styleClass.add("stage-shadow")
            maximizeButton.text = "\uE922"
            stage.x = originalLeft
            stage.y = originalTop
            stage.width = originalWidth
            stage.height = originalHeight
        }
        event.consume()
    }
}
