package dicomcopy

import java.io.{BufferedOutputStream, FileOutputStream, IOException}
import java.nio.file.{
  DirectoryNotEmptyException, DirectoryStream, FileAlreadyExistsException,
  FileVisitResult, FileVisitor, Files, NotDirectoryException, Path
}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitResult._
import java.nio.file.StandardCopyOption._

import com.pixelmed.dicom.{
  Attribute, AttributeList, AttributeTag, DicomException, TagFromName
}


/**
 * dicomcopy.CopyFiles: Class for implementing FileVisitor to copy target directories and DICOM files
 *
 * @author L. D. Nicolas May
 * @version 0.1
 * @param source              Path object of source directory
 * @param target              Path object of target directory
 * @param dicomSubDirs        String object of regex for allowable sub-directories
 * @param dicomBottomDir      String object of regex for directories that hold DICOM files
 * @param dicomSeriesDescrips String object of regex for which DICOM sequence series descriptions to include
 */
private[dicomcopy] class CopyFiles (
                 val source: Path,
                 val target: Path,
                 val dicomSubDirs: String,
                 val dicomBottomDir: String,
                 val dicomSeriesDescrips: String
               )
  extends FileVisitor[Path] {

  final private val dicomFileRegex = "^i\\d+\\.MRDC\\.\\d+$"

  /**
   * Method that implements DirectoryStream.Filter interface for filtering out DICOM files that do NOT match
   * the provided sequence series description regex
   */
  final private val filterT1T2 = new DirectoryStream.Filter[Path]() {

    override def accept(file: Path): Boolean = {
      if (file.getFileName.toString.matches(dicomFileRegex)) {
        val seriesDescription: String =
          CopyFiles.getAttributeValueFromPathTagNextTag(file,
            TagFromName.SeriesDescription, TagFromName.ManufacturerModelName)
        seriesDescription.matches(dicomSeriesDescrips)
      }
      else false
    }
  }

  /**
   * Override method of FileVisitor for actions to perform before visiting a directory Path
   * <p>
   * If a directory name matches provided sub-directory regex or it matches bottom directory regex and has DICOM
   * files with matching DICOM series sequence descriptions, the directory will be copied.
   *
   * @param dir  Source directory Path
   * @param attr Source directory Attribute
   * @return FileVisitResult action
   */
  override def preVisitDirectory(dir: Path, attr: BasicFileAttributes): FileVisitResult = {

    val targetDir: Path = target.resolve(source.relativize(dir))

    val targetDirIsIntermedDicomDir: Boolean =
      dir.equals(source) || dir.getFileName.toString.matches(dicomSubDirs)

    val targetDirIsBottomDicomDir: Boolean = dir.getFileName.toString.matches(dicomBottomDir)

    // If at a bottom DICOM directory, check that its DICOM files have series descriptions that match passed regex
    val filteredStream: DirectoryStream[Path] =
      try Files.newDirectoryStream(dir, filterT1T2)
      catch {
        case e: NotDirectoryException =>
          System.err.println(s"NotDirectoryException: " +
            s"preVisitDirectory(${dir.toString}): $e")
          null
        case e: IOException =>
          System.err.println(s"IOException: " +
            s"preVisitDirectory(${dir.toString}): $e")
          null
      }

    val targetDirHasRightDicoms: Boolean =
      targetDirIsBottomDicomDir && {
        try filteredStream.iterator().hasNext
        catch {
          case e: NullPointerException =>
            System.err.println(s"NullPointerException: " +
              s"preVisitDirectory(${dir.toString}): $e")
            false
        } finally {
          if (filteredStream != null)
            filteredStream.close()
        }
      }

    if (targetDirIsIntermedDicomDir ||
      (targetDirIsBottomDicomDir && targetDirHasRightDicoms)) {
      // printCopyFile(dir, targetDir);
      CopyFiles.copyFile(dir, targetDir)
    }
    else SKIP_SUBTREE
  }

  /**
   * Override method of FileVisitor for actions to perform after visiting a directory Path
   *
   * @param dir Source directory Path
   * @param e   IOException thrown on error
   * @return FileVisitResult action
   */
  override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {

    if (e != null)
      System.err.println(s"IOException: " +
        s"Unable to copy: postVisitDirectory(${dir.toString}, ...): $e")

    CONTINUE
  }

  /**
   * Override method of FileVisitor for actions to perform upon visiting a file Path
   * <p>
   * Only prefiltered (see preVisitDirectory override) DICOM files matching a defined regex are copied
   *
   * @param file Source file Path
   * @param attr Source file Attribute
   * @return FileVisitResult action
   */
  override def visitFile(file: Path, attr: BasicFileAttributes): FileVisitResult = {

    val targetFile = target.resolve(source.relativize(file))

    if (targetFile.getFileName.toString.matches(dicomFileRegex)) {
      // 1 - Copy file to targetFile
      // printCopyFile(file, targetFile);
      CopyFiles.copyFile(file, targetFile)

      // 2 - Deidentify the copied DICOM targetFile
      // 2a - Get DICOM targetFile AttributeList
      val attrList = CopyFiles.getAttributeListFromPath(targetFile)

      // 2b - Replace/remove private DICOM elements/attributes
      try {
        attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientName)
        attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientBirthDate)
        attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientBirthTime)
        attrList.replaceWithZeroLengthIfPresent(TagFromName.AdditionalPatientHistory)
      } catch {
        case e: DicomException =>
          System.err.println(s"DicomException: " +
            s"visitFile(${targetFile.toString}, ...): replaceWithZeroLength...(): $e")
      }

      try {
        attrList.removePrivateAttributes()
      } catch {
        case e: DicomException =>
          System.err.println(s"DicomException: " +
            s"visitFile(${targetFile.toString}, ...): removePrivateAttributes(): $e")
      }

      // 2c - Write attrList to DicomOutputStream
      val transferSyntaxUID: String = attrList
        .get(TagFromName.TransferSyntaxUID)
        .getDelimitedStringValuesOrEmptyString // https://www.dicomlibrary.com/dicom/transfer-syntax/
      val bfos: BufferedOutputStream = new BufferedOutputStream(new FileOutputStream(targetFile.toString))

      // 2d - Write edited AttributeList object (attrList) to BufferedFileOutputStream object (bfos)
      try {
        // https://www.dclunie.com/pixelmed/software/javadoc/com/pixelmed/dicom/ +
        //   AttributeList.html#write-java.io.OutputStream-java.lang.String-boolean-boolean-boolean-
        // write(BufferedFileOutputStream, TransferSyntaxUID, useMeta, useBufferedStream, closeAfterWrite)
        attrList.write(bfos, transferSyntaxUID, true, true, true)
      } catch {
        case e: DicomException =>
          System.err.println(s"DicomException: " +
            s"visitFile(${targetFile.toString}, ...): write(): $e")
        case e: IOException =>
          System.err.println(s"IOException: " +
            s"visitFile(${targetFile.toString}, ...): write(): $e")
      } finally {
        if (bfos != null) {
          bfos.close()
        }
        if (attrList != null) {
          attrList.clear()
        }
      }
    }

    CONTINUE
  }

  /**
   * Override method of FileVisitor for actions to perform upon a failed file Path visit
   *
   * @param file Source file Path
   * @param e    IOException thrown on error
   * @return FileVisitResult action
   */
  override def visitFileFailed(file: Path, e: IOException): FileVisitResult = {

    System.err.println(s"IOException: " +
      s"Unable to copy: visitFileFailed(${file.toString}): $e")

    CONTINUE
  }
}


private[dicomcopy] object CopyFiles {

  /**
   * Method for copying a source Path object to a destination Path
   *
   * @param source Path object of source directory
   * @param target Path object of target directory
   * @return FileVisitResult action
   */
  private def copyFile(source: Path, target: Path): FileVisitResult = {

    try Files.copy(source, target, COPY_ATTRIBUTES, REPLACE_EXISTING)
    catch {
      case e: FileAlreadyExistsException => // ignore
      case e: DirectoryNotEmptyException => // ignore
      case e: IOException =>
        System.err.println(s"IOException: " +
          s"Unable to copy: copyFile($source, $target): $e")
        SKIP_SUBTREE
    }

    CONTINUE
  }

  /**
   * Method to extract DICOM AttributeList from a file Path object
   *
   * @param file Path object of DICOM file
   * @return String of DICOM sequence series description
   */
  private def getAttributeListFromPath(file: Path): AttributeList = {

    val attrList = new AttributeList
    // val dicomFileRegex = "^i\\d+\\.MRDC\\.\\d+$"            // DICOM files pre-filtered in `preVisitDirectory`
    // if (file.getFileName.toString.matches(dicomFileRegex))  // DICOM files pre-filtered in `preVisitDirectory`
    try attrList.read(file.toString)
    catch {
      case e: DicomException =>
        System.err.println(s"DicomException: " +
          s"getAttributeListFromPath(${file.toString}): $e")
      case e: IOException =>
        System.err.println(s"IOException: " +
          s"getAttributeListFromPath(${file.toString}): $e")
    }

    attrList
  }

  /**
   * Method to extract DICOM sequence series description from a file Path object
   *
   * @param file Path object of DICOM file
   * @return String of DICOM sequence series description
   */
  private def getAttributeValueFromPathTagNextTag(file: Path, tag: AttributeTag, nextTag: AttributeTag): String = {

    val attrList = new AttributeList
    val attrTagValue =
      try {
        // attrList.read(file.toString)
        attrList.read(file.toString, nextTag)
        Attribute.getDelimitedStringValuesOrEmptyString(attrList, tag)
      } catch {
        case e: DicomException =>
          e.printStackTrace()
          e.toString
        case e: IOException =>
          e.printStackTrace()
          e.toString
      } finally {
        attrList.clear()
      }

    attrTagValue
  }

  /**
   * Print information about a directory or file being copied
   *
   * @param source Source Path object (directory or file)
   * @param target Target Path object (directory or file)
   */
  private def printCopyFile(source: Path, target: Path): Unit = {

    if (Files.isDirectory(source))
      println(s"Copy dir:\n  src: ${source.toString}\n  trg: ${target.toString}")
    else if (Files.isRegularFile(source))
      println(s"Copy file:\n  src: ${source.toString}\n  trg: ${target.toString}")
  }

}
