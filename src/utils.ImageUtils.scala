package jmhttp.utils

/** Collection of functions to manipulate images. */
object ImageUtils{

  import java.awt.image.BufferedImage
  import java.awt.Image

  /**  Read a BufferedImage from an image file.*/
  def readFile(file: String): BufferedImage = {
    javax.imageio.ImageIO.read(new java.io.File(file))
  }

  /** Convert Buffered Image to Image 
      Source:  [[https://stackoverflow.com/questions/13605248/java-converting-image-to-bufferedimage]]
   */
  def convertBufferedImageToImge(img: Image): BufferedImage  = {
    if(img.isInstanceOf[BufferedImage])
      return img.asInstanceOf[BufferedImage]
    val bimage = new BufferedImage(
      img.getWidth(null),
      img.getHeight(null),
      BufferedImage.TYPE_INT_ARGB
    )
    val bGr = bimage.createGraphics()
    bGr.drawImage(img, 0, 0, null)
    bGr.dispose()
    return bimage
  }

  /** Encode Image to Base64 String */
  def toBase64String(img: BufferedImage, imgType: String = "png"): String = {
    val bos = new java.io.ByteArrayOutputStream()
    try {
      javax.imageio.ImageIO.write(img, imgType, bos)
      val bytes = bos.toByteArray()
      new String(java.util.Base64.getEncoder.encode(bytes))
    } finally {
        bos.close()
    }
  }

  /** Decode Image from Base64 String */
  def fromBase64String(b64Img: String): Option[BufferedImage] =
    try {
      val bytes = java.util.Base64.getDecoder().decode(b64Img)
      Option(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)))
    } catch {
      case ex: java.lang.IllegalArgumentException
          => None
    }

  /** Scale a BufferedImage with a zoom factor increment in percent. */
  def scaleZoom(image: BufferedImage, zoom: Double) = {
     val z = 1.0 + zoom / 100.0
     val wm = (z * image.getWidth().toDouble).toInt
     val hm = (z * image.getHeight().toDouble).toInt     
     image.getScaledInstance(wm, hm, java.awt.Image.SCALE_DEFAULT)  
  }

  /** Scale a BufferedImage to the container size with a zoom factor in percent. */  
  def scaleFitZoom(image: BufferedImage, width: Int, height: Int, zoom: Double = 0.0): BufferedImage = {
    val wi = image.getWidth().toDouble
    val hi = image.getHeight().toDouble
    val z = 1.0 + zoom / 100.0
    val k = (width.toDouble / wi * z) min (height.toDouble / hi * z)
    // New image dimensions
    val wm = (k * wi).toInt
    val hm = (k * hi).toInt
    //println(s" scaleFitZoom2 wm = ${wm} hm = ${hm} / wi = ${wi} hi = ${hi} / d = ${(width, height)} ")
    val img = image.getScaledInstance(wm, hm, java.awt.Image.SCALE_DEFAULT)
    convertBufferedImageToImge(img)
  }

  /** Scale a BufferedImage to fit the container size if it is larger than the container. */  
  def scaleFitZoomIfLarger(image: BufferedImage, width: Int, height: Int, zoom: Double = 0.0) = {
    val wi = image.getWidth()
    val hi = image.getHeight()

    //println(s"scaleFitZoomIfLarger wi = ${wi} hi = ${hi} / w = ${width} h = ${width}")

    if (wi > width || hi > height)
      scaleFitZoom(image, width, height, zoom)
    else
      scaleZoom(image, zoom)
  }

  /** Show Image in a JFrame for testing purposes  */
  def show(
    img:         BufferedImage,
    title:       String  = "Image Viewer",
    exitOnClose: Boolean = false
  ) = {
    val frame = new javax.swing.JFrame("Image Viewer")
    frame.setSize(400, 400)
    val picture = new javax.swing.JLabel(new javax.swing.ImageIcon(img))
    frame.add(picture)
    if (exitOnClose)
      frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE)
    frame.setVisible(true)
    frame
  }

  def showIMG(
    img:         java.awt.Image,
    title:       String  = "Image Viewer",
    exitOnClose: Boolean = false
  ) = {
    val frame = new javax.swing.JFrame("Image Viewer")
    frame.setSize(400, 400)
    val picture = new javax.swing.JLabel(new javax.swing.ImageIcon(img))
    frame.add(picture)
    if (exitOnClose)
      frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE)
    frame.setVisible(true)
    frame
  }


  /** Read image from resource file 
       
      Note: If during development the property repl is set to "true"
      it will read the image file from the relative path instead of reading it 
      from resource.   

       In order to this function work in the Scala REPL during development run: 

      {{{
          scala -cp bin/jswing.jar -Drepl=true 
      }}}

      Example: 
      
      {{{
         scala> val image = jswing.ImageUtils.readResourceImage("icons/scalaIcon.png")
         image: java.awt.image.BufferedImage = BufferedImage@68f7aae2: type = 6 ColorModel: ...
      }}}

    */
  def readResourceImage(cls: Class[_], file: String) = {
    if (System.getProperty("repl") == "true")
      readFile(file)
    else
      {
        val img = for {
          file   <-  Option(cls.getResource(file))
          image  = javax.imageio.ImageIO.read(file)
        } yield image
        assert(!img.isEmpty, s"Error: resource image file ${file} not found.")
        img.get
      }
  }

  /** Read icon from resource file */
  def readResourceIcon(cls: Class[_], file: String) = {
    if (System.getProperty("repl") == "true")
      new javax.swing.ImageIcon(file)
    else {
      val ico = for {
        uri  <- Option(cls.getResource(file))
        icon =  new javax.swing.ImageIcon(uri)
      } yield icon
      assert(!ico.isEmpty, s"Error: resource image file ${file} not found.")
      ico.get
    }
  }
} // ---- EoF object ImageUtils -------- //


