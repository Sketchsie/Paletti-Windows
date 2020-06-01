import components.*
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.value.ChangeListener
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.control.CheckBox
import javafx.scene.control.Slider
import javafx.scene.image.Image
import javafx.scene.input.*
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import net.sourceforge.lept4j.Pix
import net.sourceforge.lept4j.util.LeptUtils
import java.io.IOException
import java.nio.file.Paths
import javax.imageio.ImageIO

interface IStage {
    val stage: Stage
}

interface INavigation {
    suspend fun next(path: String)
    suspend fun next(image: Image)
}

interface ISaveDialog {
    fun saveImage(pix: Pix)
    fun savePalette()
}

val COMBINATION_OPEN = KeyCodeCombination(KeyCode.O, KeyCodeCombination.SHORTCUT_DOWN)
val COMBINATION_CLOSE = KeyCodeCombination(KeyCode.W, KeyCodeCombination.SHORTCUT_DOWN)
val COMBINATION_SAVE = KeyCodeCombination(KeyCode.S, KeyCodeCombination.SHORTCUT_DOWN)
val COMBINATION_EXPORT_PALETTE = KeyCodeCombination(KeyCode.E, KeyCodeCombination.SHORTCUT_DOWN)
val COMBINATION_COPY_TO_CLIPBOARD = KeyCodeCombination(KeyCode.C, KeyCodeCombination.SHORTCUT_DOWN)
val COMBINATION_PASTE_FROM_CLIPBOARD = KeyCodeCombination(KeyCode.V, KeyCodeCombination.SHORTCUT_DOWN)

class MainController : IStage, INavigation, ISaveDialog, CoroutineScope {
    @FXML
    override lateinit var stage: Stage

    @FXML
    private lateinit var headerBar: HeaderBar

    @FXML
    private lateinit var fragmentContainer: StackPane

    @FXML
    private lateinit var fragment: IFragment

    @FXML
    private lateinit var notification: Notification

    @FXML
    private lateinit var slider: Slider

    @FXML
    private lateinit var monoSwitch: CheckBox

    @FXML
    private lateinit var colorPalette: HBox

    override val coroutineContext = Dispatchers.JavaFx
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var left = 0.0
    private var top = 0.0
    private val sliderCountListener: ChangeListener<Number> by lazy {
        ChangeListener<Number> { _, _, count ->
            val currentCount = colorPalette.children.size
            val newCount = count.toInt()
            if (currentCount > newCount) {
                colorPalette.children.remove(newCount, currentCount)
            } else {
                while (colorPalette.children.size < newCount) {
                    colorPalette.children.add(ColorTile())
                }
            }
        }
    }

    fun initialize() {
        while (colorPalette.children.size < slider.value.toInt()) {
            colorPalette.children.add(ColorTile())
        }
        slider.valueProperty().addListener(sliderCountListener)
        headerBar.isMaximized.addListener { _, _, isMaximized ->
            if (isMaximized) {
                stage.scene.root.styleClass.remove("stage-shadow")
            } else {
                stage.scene.root.styleClass.add("stage-shadow")
            }
        }
    }

    fun onHeaderBarPressed(event: MouseEvent) {
        offsetX = stage.x - event.screenX
        offsetY = stage.y - event.screenY
        event.consume()
    }

    fun onHeaderBarDragged(event: MouseEvent) {
        if (!headerBar.isMaximized.value) {
            stage.x = event.screenX + offsetX
            stage.y = event.screenY + offsetY
        }
        event.consume()
    }

    fun onResizeBegin(event: MouseEvent) {
        left = event.screenX
        top = event.screenY
        event.consume()
    }

    fun onResize(event: MouseEvent) {
        if (!headerBar.isMaximized.value) {
            if (stage.width + event.screenX - left >= stage.minWidth) {
                stage.width = stage.width + event.screenX - left
            } else {
                event.consume()
                return
            }
            if (stage.height + event.screenY - top >= stage.minHeight) {
                stage.height = stage.height + event.screenY - top
            } else {
                event.consume()
                return
            }
            left = event.screenX
            top = event.screenY
        }
        event.consume()
    }

    fun onDropareaClick(event: MouseEvent) {
        openFileDialog()
        event.consume()
    }

    fun onDragOver(event: DragEvent) {
        if (event.dragboard.hasFiles()) {
            event.acceptTransferModes(*TransferMode.ANY)
        }
        event.consume()
    }

    fun onDragDropped(event: DragEvent) {
        val droppedFile = event.dragboard.files.first().absolutePath
        launch {
            try {
                fragment.onLoad(droppedFile)
            } catch (e: PalettiError) {
                notification.show(e)
            }
        }
        event.consume()
    }

    fun onScroll(event: ScrollEvent) {
        if (event.deltaY > 0) {
            slider.value++
        } else {
            slider.value--
        }
    }

    fun onKeyPressed(event: KeyEvent) {
        try {
            when {
                event.code == KeyCode.X -> monoSwitch.isSelected = !monoSwitch.isSelected
                COMBINATION_OPEN.match(event) -> openFileDialog()
                COMBINATION_CLOSE.match(event) -> stage.close()
                COMBINATION_PASTE_FROM_CLIPBOARD.match(event) -> {
                    if (Clipboard.getSystemClipboard().hasImage()) {
                        val image = Clipboard.getSystemClipboard().image
                        launch { fragment.onLoad(image) }
                    }
                }
                else -> {
                    fragment.onShortcut(event)
                    return
                }
            }
            event.consume()
        } catch (e: Uninitialized) {
            notification.show(e)
        }
    }

    override suspend fun next(path: String) {
        val sliderValueProperty = SimpleIntegerProperty(slider.value.toInt())
        slider.valueProperty().bindBidirectional(sliderValueProperty)
        val fragment = ImageFragment(sliderValueProperty, monoSwitch.selectedProperty(), this)
        try {
            fragment.onLoad(path)
            setup(fragment)
            setColorPalette(fragment.colors.value)
        } catch (e: PalettiError) {
            notification.show(e)
        }
    }

    override suspend fun next(image: Image) {
        val sliderValueProperty = SimpleIntegerProperty(slider.value.toInt())
        slider.valueProperty().bindBidirectional(sliderValueProperty)
        val fragment = ImageFragment(sliderValueProperty, monoSwitch.selectedProperty(), this)
        try {
            fragment.onLoad(image)
            setup(fragment)
            setColorPalette(fragment.colors.value)
        } catch (e: PalettiError) {
            notification.show(e)
        }
    }

    override fun saveImage(pix: Pix) {
        val fileChooser = FileChooser()
        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("PNG Image", "*.png"))
        fileChooser.showSaveDialog(stage)?.let {
            try {
                ImageIO.write(LeptUtils.convertPixToImage(pix), "png", it)
                notification.show("Saved image to ${Paths.get(it.toURI())}")
            } catch (e: IOException) {
                e.message?.let { error -> notification.show(error) } ?: e.printStackTrace()
            }
        }
    }

    override fun savePalette() {
        val fileChooser = FileChooser()
        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("PNG Image", "*.png"))
        fileChooser.showSaveDialog(stage)?.let {
            try {
                val viewport = Rectangle2D(0.0, 2.0, colorPalette.width, colorPalette.height - 2.0)
                ImageIO.write(
                    SwingFXUtils.fromFXImage(colorPalette.snapshot(SnapshotParameters().apply {
                        this.viewport = viewport
                    }, null), null),
                    "png", it
                )
                notification.show("Saved palette to ${Paths.get(it.toURI())}")
            } catch (e: IOException) {
                e.message?.let { error -> notification.show(error) } ?: e.printStackTrace()
            }
        }
    }

    private fun setup(imageFragment: ImageFragment) {
        imageFragment.imageView.fitWidthProperty().bind(fragmentContainer.widthProperty())
        imageFragment.imageView.fitHeightProperty().bind(fragmentContainer.heightProperty())
        slider.valueProperty().removeListener(sliderCountListener)
        imageFragment.colors.addListener { _, _, colors -> setColorPalette(colors) }
        fragmentContainer.children.removeAt(0)
        fragmentContainer.children.add(0, imageFragment)
        fragment = imageFragment
    }

    private fun setColorPalette(colors: Array<Color>) {
        val currentCount = colorPalette.children.size
        if (currentCount > colors.size) {
            colorPalette.children.remove(colors.size, currentCount)
        } else {
            while (colorPalette.children.size < colors.size) {
                colorPalette.children.add(ColorTile())
            }
        }
        colors.forEachIndexed { index, color ->
            (colorPalette.children[index] as ColorTile).setColor(color)
        }
    }

    private fun openFileDialog() {
        val fileChooser = FileChooser()
        fileChooser.title = "Select an image"
        fileChooser.showOpenDialog(stage)?.let {
            launch {
                try {
                    fragment.onLoad(it.absolutePath)
                } catch (e: PalettiError) {
                    notification.show(e)
                }
            }
        }
    }
}
