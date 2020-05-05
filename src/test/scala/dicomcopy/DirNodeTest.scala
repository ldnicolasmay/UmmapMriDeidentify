package dicomcopy

import java.nio.file.{Path, Paths}

import org.scalatest.funsuite.AnyFunSuite

/**
 * Test DICOMs publicly available at http://www.pcir.org/researchers/downloads_available.html
 * Download at http://www.pcir.org/researchers/98890234_20030505_MR.html
 */

class DirNodeTest extends AnyFunSuite {

  val origDirStr: String = "/Users/ldmay/IdeaProjects/UmmapMriDeidentify/dicom/orig"
  val origDirPath: Path = Paths.get(origDirStr)
  val oneEmptyDirStr: String = "/Users/ldmay/IdeaProjects/UmmapMriDeidentify/dicom/one_empty"
  val oneEmptyPath: Path = Paths.get(oneEmptyDirStr)

  // countSubNode()
  test("MR1 directory should have 3 subnodes") {
    val sourceDirStr: String = origDirStr + "/98890234_20030505_MR/98890234/20030505/MR/MR1"
    val sourceDirPath: Path = Paths.get(sourceDirStr)
    val sourceDirNode: DirNode = DirNode.apply(sourceDirPath, 0, """^MR\d{1,3}$""", """^\d{4,5}$""")
    // assert
    assert(sourceDirNode.countSubNodes() === 3)
  }
  test("MR2 directory should have 7 subnodes") {
    val sourceDirStr: String = origDirStr + "/98890234_20030505_MR/98890234/20030505/MR/MR2"
    val sourceDirPath: Path = Paths.get(sourceDirStr)
    val sourceDirNode: DirNode = DirNode.apply(sourceDirPath, 0, """^MR\d{1,3}$""", """^\d{4,5}$""")
    // assert
    assert(sourceDirNode.countSubNodes() === 7)
  }

  // substituteRootNodeName
  test("substituteRootNodeName applied to ./dicom/one_empty should yield ./dicom/orig") {
    val origDirNode: DirNode =
      DirNode.apply(
        origDirPath,
        depth = 0,
        intermedDirsRegex = """^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR\d{1,3}$""",
        dicomFileRegex = """^\d{4,5}$""")
    val oneEmptyDirNode: DirNode =
      DirNode.apply(
        oneEmptyPath,
        depth = 0,
        intermedDirsRegex = """^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR\d{1,3}$""",
        dicomFileRegex = """^\d{4,5}$""")
    // assert
    assert {
      origDirNode ==
        oneEmptyDirNode
          .substituteRootNodeName(
            oneEmptyDirNode.dirPath.getFileName.toString,
            origDirNode.dirPath.getFileName.toString
          )
    }
  }

  // filterChildDirNodesWith
  // -  filterChildDirNodesWith(nonemptyDirNodesFilter)
  test("nonemptyDirNodesFilter should be filter out empty directory") {
    val origDirNode: DirNode =
      DirNode.apply(
        origDirPath,
        depth = 0,
        intermedDirsRegex = """^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR\d{1,3}$""",
        dicomFileRegex = """^\d{4,5}$""")
    val oneEmptyDirNode: DirNode =
      DirNode.apply(
        oneEmptyPath,
        depth = 0,
        intermedDirsRegex = """^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR\d{1,3}$|^empty$""",
        dicomFileRegex = """^\d{4,5}$""")
    // assert
    assert {
      origDirNode.toString ==
        oneEmptyDirNode
          .filterChildDirNodesWith(nonemptyDirNodesFilter)
          .substituteRootNodeName(
            oneEmptyDirNode.dirPath.getFileName.toString,
            origDirNode.dirPath.getFileName.toString
          )
          .toString
    }
  }
  // -  filterChildDirNodesWith(intermediateDirNameFilter)
  test("intermediateDirNameFilter should keep only regexed directories") {
    val origDirNode: DirNode =
      DirNode.apply(
        origDirPath,
        depth = 0,
        intermedDirsRegex = """^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR[12]$""",
        dicomFileRegex = """^\d{4,5}$""")
    val origDirNodeFiltered: DirNode =
      DirNode.apply(
        origDirPath,
        depth = 0,
        intermedDirsRegex = """^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR1$""",
        dicomFileRegex = """^\d{4,5}$""")
    // assert
    assert {
      origDirNodeFiltered.toString ==
        origDirNode
          .filterChildDirNodesWith(
            intermedDirNameFilter("""^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR1$""")(_)
          ).toString
    }
  }

  // - filterChildDirNodesWith(numberOfFilesFilter)
  test("numberOfFilesFilter should keep only branches of tree with <= 7 files") {
    val origDirNode: DirNode =
      DirNode.apply(
        origDirPath,
        depth = 0,
        intermedDirsRegex = """^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR\d{1,3}$""",
        dicomFileRegex = """^\d{4,5}$""")
    val origDirNodeFiltered: DirNode =
      DirNode.apply(
        origDirPath,
        depth = 0,
        intermedDirsRegex = """^98890234_20030505_MR$|^98890234$|^20030505$|^MR$|^MR[12]$""",
        dicomFileRegex = """^\d{4,5}$""")
    // assert
    assert {
      origDirNodeFiltered.toString ==
        origDirNode
          .filterChildDirNodesWith(numberOfFilesFilter(7)(_))
          .toString
    }
  }

}
