import java.io.IOException
import java.nio.file.Path
import com.pixelmed.dicom.{
  Attribute,
  AttributeList,
  AttributeTag,
  DicomException,
  TagFromName
}


package object dicomcopy {

  /**
   * Extracts DICOM sequence attribute as a String from a file Path object
   * Minimizes how much of a DICOM file needs to be read by extracting AttributeTags up to `nextTag`
   *
   * @param file    Path object of DICOM file
   * @param tag     AttributeTag object to retrieval
   * @param nextTag AttributeTag object that's just past tag to retrieve
   * @return String of DICOM sequence series description
   */
  private def getAttributeValueFromPathTagNextTag(file: Path,
                                                  tag: AttributeTag,
                                                  nextTag: AttributeTag): String = {
    val attrList = new AttributeList
    val attrTagValue =
      try {
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
   * Predicate to determine whether DirNode object has any DirNodes or FileNodes
   *
   * @param dirNode DirNode object to evaluate
   * @return Boolean
   */
  def nonemptyDirNodesFilter(dirNode: DirNode): Boolean = {
    dirNode.childDirNodes.nonEmpty || dirNode.childFileNodes.nonEmpty
  }

  /**
   * Predicate to determine whether DirNode object directory name matches
   *
   * @param intermedDirRegex String regex of intermediate directories leading to or containing DICOM files
   * @param dirNode          DirNode object to evaluate
   * @return Boolean
   */
  def intermedDirNameFilter(intermedDirRegex: String)(dirNode: DirNode): Boolean = {
    dirNode.dirPath.getFileName.toString
      .matches(intermedDirRegex)
  }

  /**
   * Predicate to determine whether DirNode object contains nor more files than the passed Int
   *
   * @param maxFileCount Max number of files
   * @param dirNode      DirNode object to be evaluated
   * @return Boolean
   */
  def numberOfFilesFilter(maxFileCount: Int)(dirNode: DirNode): Boolean = {
    dirNode.childFileNodes.length <= maxFileCount
  }

  /**
   * Predicate to determine whether FileNode object's path file name matches passed regex
   *
   * @param dicomFileRegex String regex of DICOM file names
   * @param fileNode       FileNode object to evaluate
   * @return Boolean
   */
  def dicomFileFilter(dicomFileRegex: String)(fileNode: FileNode): Boolean = {
    fileNode.filePath.getFileName.toString
      .matches(dicomFileRegex)
  }

  /**
   * Predicate to help filter for DICOM files that match file name regex and have matching DICOM Series Descriptions
   *
   * @param seriesDescriptionRegex Regex String to match against DICOM Series Description element names
   * @param fileNode               FileNode object whose filename must match `dicomFileRegex` and
   *                               to extract target DICOM Series Description element name from
   * @return Boolean
   */
  def dicomFileSeriesDescripFilter(seriesDescriptionRegex: String)
                                  (fileNode: FileNode): Boolean = {
    val seriesDescription: String =
      getAttributeValueFromPathTagNextTag(
        fileNode.filePath,
        TagFromName.SeriesDescription,
        TagFromName.ManufacturerModelName
      )
    seriesDescription.matches(seriesDescriptionRegex)
  }

  /**
   * Determines whether DirNode object exists in `dirNodeTreeToSearch`
   *
   * @param dirNodeTreeToSearch DirNode object tree to search for DirNode object
   * @param dirNode             DirNode object to find in `dirNodeTreeToSearch`
   * @return Boolean
   */
  def childDirNodeExistsIn(dirNodeTreeToSearch: DirNode)
                          (dirNode: DirNode): Boolean = {
    if (dirNode.dirPath.toString == dirNodeTreeToSearch.dirPath.toString)
      true
    else
      dirNodeTreeToSearch.childDirNodes
        .map(cdn => childDirNodeExistsIn(cdn)(dirNode))
        .foldLeft(false)(_ || _)
  }

  /**
   * Determines whether `fileNode` exists in `dirNodeTreeToSearch`
   *
   * @param dirNodeTreeToSearch DirNode object tree to search for `fileNode`
   * @param fileNode            FileNode object to find in `dirNodeTreeToSearch`
   * @return Boolean
   */
  @scala.annotation.tailrec
  def childFileNodeExistsIn(dirNodeTreeToSearch: DirNode)
                           (fileNode: FileNode): Boolean = {
    if (dirNodeTreeToSearch.childFileNodes.exists(
      _.filePath.toString == fileNode.filePath.toString)) {
      true
    } else if (dirNodeTreeToSearch.childDirNodes.nonEmpty) {
      val dirNodeTreeToSearchPathString = dirNodeTreeToSearch.dirPath.toString
      val fileNodePathString = fileNode.filePath.toString
      val pathDiffArray: Array[String] =
        fileNodePathString
          .substring(dirNodeTreeToSearchPathString.length + 1)
          .split("/")

      // recurse down
      val targetNode =
        dirNodeTreeToSearch
          .findDirNode(
            s"${dirNodeTreeToSearch.dirPath.toString}/${pathDiffArray.head}")
      targetNode match {
        case Some(newDirNodeTreeToSearch) =>
          childFileNodeExistsIn(newDirNodeTreeToSearch)(fileNode)
        case None => false
      }
    } else {
      false
    }
  }

}
