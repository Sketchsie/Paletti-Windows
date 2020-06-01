package components

import IStage
import javafx.beans.NamedArg
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.stage.Screen

class HeaderBar(@NamedArg("stage") private val stage: IStage) : HBox() {
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
        stage.stage.close()
        event.consume()
    }

    fun onMinimize(event: ActionEvent) {
        stage.stage.isIconified = true
        event.consume()
    }

    fun onMaximize(event: ActionEvent) {
        isMaximized.value = !isMaximized.value
        if (isMaximized.value) {
            maximizeButton.text = "\uE923"
            originalWidth = stage.stage.width
            originalHeight = stage.stage.height
            originalLeft = stage.stage.x
            originalTop = stage.stage.y
            Screen.getPrimary().visualBounds.apply {
                stage.stage.width = width
                stage.stage.height = height
                stage.stage.x = minX
                stage.stage.y = minY
            }
        } else {
            maximizeButton.text = "\uE922"
            stage.stage.x = originalLeft
            stage.stage.y = originalTop
            stage.stage.width = originalWidth
            stage.stage.height = originalHeight
        }
        event.consume()
    }
}
