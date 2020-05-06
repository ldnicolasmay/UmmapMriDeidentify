package dicomcopy

import java.io.{BufferedOutputStream, FileOutputStream, IOException}
import java.nio.file.attribute.{BasicFileAttributes, FileTime}
import java.nio.file.FileVisitResult._
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.{CopyOption, DirectoryNotEmptyException, FileAlreadyExistsException}
import java.nio.file.{FileVisitResult, FileVisitor, Files, Path}

import com.pixelmed.dicom.{Attribute, AttributeList, DicomException, TagFromName}


/**
 * Class for defining requisite source path, target path, copy options, verbose flag
 *
 * @param sourceDirPath Path of source directory
 * @param targetDirPath Path of target directory
 * @param copyOptions   Seq of possible REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINK
 * @param verbose       Boolean flag for verbose printing
 */
class FileCopier(
                  val sourceDirPath: Path,
                  val targetDirPath: Path,
                  val copyOptions: Seq[CopyOption],
                  val verbose: Boolean
                )
  extends FileVisitor[Path] {

  /**
   * Performs actions before visiting a directory
   *
   * @param dir  Path of directory to act on before visit
   * @param attr BasicFileAttributes of `dir`
   * @return FileVisitResult to CONTINUE or SKIP_SUBTREE
   */
  override def preVisitDirectory(dir: Path, attr: BasicFileAttributes): FileVisitResult = {
    val target: Path = targetDirPath.resolve(sourceDirPath.relativize(dir))
    try {
      Files.copy(dir, target, copyOptions: _*)
      if (verbose) println(s"Copy: ${dir.toString} => ${target.toString}")
    }
    catch {
      case e: DirectoryNotEmptyException => // ignore
      case e: IOException =>
        System.err.println(s"Unable to copy: preVisitDirectory(${dir.toString}): $e")
        SKIP_SUBTREE
    }
    CONTINUE
  }

  /**
   * Performs actions after visiting a directory
   *
   * @param dir Path of directory to act on after visit
   * @param e   IOException thrown from directory visit
   * @return FileVisitResult to CONTINUE
   */
  override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {
    if (e == null) {
      val target = targetDirPath.resolve(sourceDirPath.relativize(dir))
      try {
        val basicAttrs: BasicFileAttributes = Files.readAttributes(dir, classOf[BasicFileAttributes])
        Files.setAttribute(target, "lastModifiedTime", basicAttrs.lastModifiedTime(): FileTime)
        Files.setAttribute(target, "creationTime", basicAttrs.creationTime(): FileTime)
        Files.setAttribute(target, "lastAccessTime", basicAttrs.lastAccessTime(): FileTime)
      }
      catch {
        case e: IOException =>
          System.err.println(s"Unable to copy attributes: postVisitDirectory(${dir.toString}): $e")
      }
    }
    CONTINUE
  }

  /**
   * Performs actions when visiting a Java "File" object (directory or file)
   *
   * @param file File to visit
   * @param attr BasicFileAttributes of `file`
   * @return FileVisitResult to CONTINUE
   */
  override def visitFile(file: Path, attr: BasicFileAttributes): FileVisitResult = {
    val target = targetDirPath.resolve(sourceDirPath.relativize(file))
    FileCopier.copyFile(file, target, copyOptions, verbose)
    FileCopier.deidentifyDicomFile(file, attr, target, copyOptions)
    CONTINUE
  }

  /**
   * Performs actions when visiting a Java "file" object (directory or file) fails
   *
   * @param file File whose visit failed
   * @param e    IOException thrown by failed file visit
   * @return FileVisitResult to CONTINUE
   */
  override def visitFileFailed(file: Path, e: IOException): FileVisitResult = {
    System.err.println(s"Unable to copy: visitFileFailed(${file.toString}): $e")
    CONTINUE
  }

}

/**
 * Companion object for FileCopier class
 */
object FileCopier {

  /**
   * Copies file from source to target
   *
   * @param source      Path of source file to copy
   * @param target      Path of target where `source` is to be copied
   * @param copyOptions Seq of possible REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINK
   * @param verbose     Boolean flag for verbose printing
   */
  def copyFile(source: Path,
               target: Path,
               copyOptions: Seq[CopyOption],
               verbose: Boolean): Unit = {
    try {
      Files.copy(source, target, copyOptions: _*)
      if (verbose) println(s"Copy: ${source.toString} => ${target.toString}")
    }
    catch {
      case e: FileAlreadyExistsException => // ignore
      case e: IOException =>
        System.err.println(s"Unable to copy: copyFile(${source.toString}): $e")
    }
  }

  /**
   * Deidentify AttributeList of DICOM file via helper functions
   *
   * @param sourceDicomFile Source Path of DICOM file
   * @param attr            BasicFileAttributes of `sourceDicomFile`
   * @param targetDicomFile Target Path of DICOM file
   * @param copyOptions     Seq of possible REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINK
   */
  private def deidentifyDicomFile(sourceDicomFile: Path,
                                  attr: BasicFileAttributes,
                                  targetDicomFile: Path,
                                  copyOptions: Seq[CopyOption]): Unit = {
    // 1 - Get DICOM targetFile AttributeList
    val attrList = FileCopier.getAttributeListFromPath(targetDicomFile)
    // 2 - Reformat PatientID string: hlp17umm01234 => UM00001234
    FileCopier.reformatPatientId(targetDicomFile, attrList)
    // 3 - Replace/remove private DICOM elements/attributes
    FileCopier.replacePrivateDicomElements(targetDicomFile, attrList)
    FileCopier.removePrivateElements(targetDicomFile, attrList)
    // 4 - Write deidentified AttributeList object to file
    FileCopier.writeAttributeListToFile(sourceDicomFile, attrList, targetDicomFile, copyOptions)
  }

  /**
   * Method to extract DICOM AttributeList from a file Path object
   *
   * @param dicomFile Path object of DICOM file
   * @return String of DICOM sequence series description
   */
  private def getAttributeListFromPath(dicomFile: Path): AttributeList = {
    val attrList = new AttributeList
    try
      attrList.read(dicomFile.toString)
    catch {
      case e: DicomException =>
        System.err.println(s"getAttributeListFromPath(${dicomFile.toString}): $e")
      case e: IOException =>
        System.err.println(s"getAttributeListFromPath(${dicomFile.toString}): $e")
    }
    attrList
  }

  /**
   * Reformat PatientId elements to UMMAP ID format
   *
   * @param dicomFile Path object of DICOM file
   * @param attrList AttributeList object from DICOM file
   */
  private def reformatPatientId(dicomFile: Path, attrList: AttributeList): Unit = {
    val idPrefix = """^hlp17umm|^bmh17umm|^hlp14umm|^17umm""".r
    val patientIdBefore: String =
      Attribute.getDelimitedStringValuesOrEmptyString(attrList, TagFromName.PatientID)
    if (patientIdBefore.matches("""^hlp17umm\d{5}$|^bmh17umm\d{5}$|^hlp14umm\d{5}$|^17umm\d{5}$""")) {
      val patientIdAfter: String = idPrefix.replaceFirstIn(patientIdBefore, "UM000")
      attrList.replaceWithValueIfPresent(TagFromName.PatientID, patientIdAfter)
    }
    else
      throw new Exception(s"PatientID $patientIdBefore in ${dicomFile.toString} does not match expected format")
  }

  /**
   * Replace hard-coded attributes/elements with zero-length Strings
   *
   * @param dicomFile Copied DICOM file that needs to be deidentified
   * @param attrList  AttributeList object from DICOM file
   */
  private def replacePrivateDicomElements(dicomFile: Path, attrList: AttributeList): Unit = {
    try {
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.SeriesDescription)      // Preserve for NACC
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientID)              // Preserve for NACC
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.Manufacturer)           // Preserve for NACC
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.ManufacturersModelName) // Preserve for NACC
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.StudyInstanceUID)       // Preserve for NACC
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.StudyDate)              // Preserve for NACC
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.SeriesInstanceUID)      // Preserve for NACC
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.MagneticFieldStrength)  // Preserve for NACC
      attrList.replaceWithZeroLengthIfPresent(TagFromName.InstanceCreatorUID)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.SOPInstanceUID)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.AccessionNumber)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.InstitutionName)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.InstitutionAddress)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.ReferringPhysicianName)
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.ReferringPhysicianAddress) // Deprecated
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.ReferringPhysicianTelephoneNumbers) // Deprecated
      attrList.replaceWithZeroLengthIfPresent(TagFromName.StationName)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.StudyDescription)
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.RequestingPhysician) // Deprecated
      attrList.replaceWithZeroLengthIfPresent(TagFromName.InstitutionalDepartmentName)
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.InstanceNumber) // Needed for correct DICOM viewer ordering
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PhysiciansOfRecord)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PerformingPhysicianName)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.NameOfPhysiciansReadingStudy)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.OperatorsName)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.AdmittingDiagnosesDescription)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.ReferencedSOPInstanceUID)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.DerivationDescription)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientName)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientBirthDate)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientBirthTime)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientSex)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.OtherPatientIDs)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.OtherPatientNames)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientAge)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientSize)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientWeight)
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.MedicalRecordLocator) // Deprecated
      attrList.replaceWithZeroLengthIfPresent(TagFromName.EthnicGroup)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.Occupation)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.AdditionalPatientHistory)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.PatientComments)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.DeviceSerialNumber)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.ProtocolName)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.StudyID)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.FrameOfReferenceUID)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.SynchronizationFrameOfReferenceUID)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.ImageComments)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.RequestAttributesSequence)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.UID)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.ContentSequence)
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.StorageMediaFileSetUID) // Deprecated
      attrList.replaceWithZeroLengthIfPresent(TagFromName.ReferencedFrameOfReferenceUID)
      // attrList.replaceWithZeroLengthIfPresent(TagFromName.RelatedFrameOfReferenceUID) // Deprecated
      attrList.replaceWithZeroLengthIfPresent(TagFromName.RescaleIntercept)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.RescaleSlope)
      attrList.replaceWithZeroLengthIfPresent(TagFromName.RescaleType)
    }
    catch {
      case e: DicomException =>
        System.err.println(s"replacePrivateDicomElements(${dicomFile.toString}, ...): $e")
    }
  }

  /**
   * Remove all private attributes/elements from AttributeList object
   *
   * @param dicomFile Copied DICOM file that needs to be deidentified
   * @param attrList  AttributeList object from DICOM file
   */
  private def removePrivateElements(dicomFile: Path, attrList: AttributeList): Unit = {
    try
      attrList.removePrivateAttributes()
    catch {
      case e: DicomException =>
        System.err.println(s"removePrivateElements(${dicomFile.toString}, ...): $e")
    }
  }

  /**
   * Write AttributeList object to Path object
   *
   * @param sourceDicomFile Source Path of DICOM file
   * @param targetDicomFile Target Path of DICOM file
   * @param copyOptions     Seq of possible REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINK
   * @param attrList        AttributeList object from `sourceDicomFile`
   */
  private def writeAttributeListToFile(sourceDicomFile: Path,
                                       attrList: AttributeList,
                                       targetDicomFile: Path,
                                       copyOptions: Seq[CopyOption]): Unit = {
    // 1 - Write attrList to DicomOutputStream
    val transferSyntaxUID: String = attrList
      .get(TagFromName.TransferSyntaxUID)
      .getDelimitedStringValuesOrEmptyString // https://www.dicomlibrary.com/dicom/transfer-syntax/
    val bfos: BufferedOutputStream = new BufferedOutputStream(new FileOutputStream(targetDicomFile.toString))
    // 2 - Write edited AttributeList object (attrList) to BufferedFileOutputStream object (bfos)
    try {
      // https://www.dclunie.com/pixelmed/software/javadoc/com/pixelmed/dicom/ +
      //   AttributeList.html#write-java.io.OutputStream-java.lang.String-boolean-boolean-boolean-
      // write(BufferedFileOutputStream, TransferSyntaxUID, useMeta, useBufferedStream, closeAfterWrite)
      attrList.write(bfos, transferSyntaxUID, true, true, true)
      if (copyOptions.contains(COPY_ATTRIBUTES)) {
        Files.setLastModifiedTime(targetDicomFile, Files.getLastModifiedTime(sourceDicomFile, NOFOLLOW_LINKS))
        Files.setPosixFilePermissions(targetDicomFile, Files.getPosixFilePermissions(sourceDicomFile, NOFOLLOW_LINKS))
      }
    }
    catch {
      case e: DicomException =>
        System.err.println(s"writeAttributeListToFile(${targetDicomFile.toString}) DicomExc: $e")
      case e: IOException =>
        System.err.println(s"writeAttributeListToFile(${targetDicomFile.toString}) IOExc: $e")
    }
    finally {
      if (bfos != null)
        bfos.close()
      if (attrList != null)
        attrList.clear()
    }
  }

}
