package components

import BlackWhitePix
import COMBINATION_COPY_TO_CLIPBOARD
import COMBINATION_EXPORT_PALETTE
import COMBINATION_SAVE
import INavigation
import IPix
import ISaveDialog
import LeptonicaReadError
import PosterizedPix
import Uninitialized
import com.sun.jna.ptr.PointerByReference
import javafx.beans.NamedArg
import javafx.beans.property.BooleanProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.KeyEvent
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import net.sourceforge.lept4j.Leptonica1
import net.sourceforge.lept4j.Pix
import net.sourceforge.lept4j.util.LeptUtils
import kotlin.coroutines.CoroutineContext

interface IFragment {
    suspend fun onLoad(path: String)
    suspend fun onLoad(image: Image)
    fun onShortcut(event: KeyEvent)
}

class InitialFragment(@NamedArg("navigation") private val navigation: INavigation) : VBox(), IFragment {
    init {
        FXMLLoader(javaClass.getResource("FragmentInitial.fxml")).apply {
            setRoot(this@InitialFragment)
            setController(this@InitialFragment)
            load()
        }
    }

    override suspend fun onLoad(path: String) {
        navigation.next(path)
    }

    override suspend fun onLoad(image: Image) {
        navigation.next(image)
    }

    override fun onShortcut(event: KeyEvent) {
        when {
            COMBINATION_SAVE.match(event) ||
                COMBINATION_EXPORT_PALETTE.match(event) ||
                COMBINATION_COPY_TO_CLIPBOARD.match(event) -> {
                throw Uninitialized
            }
        }
        event.consume()
    }
}

class ImageFragment(
    private val colorsCount: IntegerProperty,
    private val isBlackWhite: BooleanProperty,
    private val saveDialog: ISaveDialog
) : StackPane(), IFragment, CoroutineScope {
    @FXML
    lateinit var imageView: ImageView

    val colors = SimpleObjectProperty<Array<Color>>()
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private var time: Long = 0
    private var pixSource: Pix? = null
    private var pix: IPix? = null

    init {
        FXMLLoader(javaClass.getResource("FragmentImage.fxml")).apply {
            setRoot(this@ImageFragment)
            setController(this@ImageFragment)
            load()
        }
        this.colorsCount.addListener { _, _, count ->
            time = System.nanoTime()
            launch {
                delay(100)
                val delta = System.nanoTime()
                // Difference in NANOseconds
                if (delta - time >= 100000000) {
                    posterize(count.toInt(), isBlackWhite.value)
                }
            }
        }
        this.isBlackWhite.addListener { _, _, isBlackWhite ->
            launch { posterize(colorsCount.value, isBlackWhite) }
        }
    }

    override suspend fun onLoad(path: String) {
        withContext(Dispatchers.IO) {
            pix?.src?.let { Leptonica1.pixDestroy(PointerByReference(it.pointer)) }
            pixSource?.let { Leptonica1.pixDestroy(PointerByReference(it.pointer)) }
            pixSource = Leptonica1.pixRead(path) ?: throw LeptonicaReadError
        }
        posterize(colorsCount.value, isBlackWhite.value)
    }

    override suspend fun onLoad(image: Image) {
        pix?.src?.let { Leptonica1.pixDestroy(PointerByReference(it.pointer)) }
        pixSource?.let { Leptonica1.pixDestroy(PointerByReference(it.pointer)) }
        pixSource = LeptUtils.convertImageToPix(SwingFXUtils.fromFXImage(image, null))
        posterize(colorsCount.value, isBlackWhite.value)
    }

    override fun onShortcut(event: KeyEvent) {
        when {
            COMBINATION_SAVE.match(event) -> {
                pix?.let { saveDialog.saveImage(it.src) }
            }
            COMBINATION_EXPORT_PALETTE.match(event) -> {
                pix?.let { saveDialog.savePalette() }
            }
            COMBINATION_COPY_TO_CLIPBOARD.match(event) -> {
                Clipboard.getSystemClipboard().setContent(ClipboardContent().apply {
                    putImage(imageView.image)
                })
            }
        }
        event.consume()
    }

    private suspend fun posterize(colors: Int, isBlackWhite: Boolean) {
        pix = if (isBlackWhite) {
            BlackWhitePix(PosterizedPix(pixSource ?: return, colors).src)
        } else {
            PosterizedPix(pixSource ?: return, colors)
        }
        imageView.image = SwingFXUtils.toFXImage(pix?.image, null)
        withContext(Dispatchers.JavaFx) {
            this@ImageFragment.colors.set(pix?.colors)
        }
    }
}
