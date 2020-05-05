package dicomcopy

import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.{COPY_ATTRIBUTES, REPLACE_EXISTING}
import java.nio.file.{CopyOption, Path, Paths}
import java.util.Calendar
import java.util.concurrent.Callable
import picocli.CommandLine
import picocli.CommandLine.{Command, Option, Parameters}


@Command(
  name = "java -jar UmmapMriTreeScratch.jar",
  mixinStandardHelpOptions = true,
  version = Array("ConstructTree 0.1"),
  description = Array("Builds a tree from a source directory."),
  sortOptions = false,
  showDefaultValues = true,
  headerHeading = "Usage:%n",
  synopsisHeading = "%n",
  descriptionHeading = "%nDescription:%n%n",
  parameterListHeading = "%nParameters:%n",
  optionListHeading = "%nOptions:%n"
)
class ConstructNodeTree extends Callable[Int] {

  @Parameters(index = "0", description = Array("Source directory"), paramLabel = "SOURCE_DIR")
  var sourceDirStr: String = _

  @Parameters(index = "1", description = Array("Target directory"), paramLabel = "TARGET_DIR")
  var targetDirStr: String = _

  @Option(
    names = Array("-i", "--intermed-dirs-regex"),
    arity = "1..*",
    description = Array("Regex string of allowable intermediate directories"),
    paramLabel = "INTERMED_DIRS_REGEX",
    required = false,
    defaultValue = "^hlp17umm\\d{5}_\\d{5}$|^dicom$|^s\\d{5}$"
  )
  var intermedDirsRegexArray: Array[String] = _

  @Option(
    names = Array("-d", "--dicom-file-regex"),
    arity = "1..*",
    description = Array("Regex string of allowable DICOM file names"),
    paramLabel = "DICOM_FILE_REGEX",
    required = false,
    defaultValue = "^i\\d+\\.MRDC\\.\\d+$"
  )
  var dicomFileRegexArray: Array[String] = _

  @Option(
    names = Array("-s", "--series-description-regex"),
    arity = "1..*",
    description = Array("Regex"),
    paramLabel = "SERIES_DESCRIPTION_REGEX",
    required = false,
    defaultValue = "^t1sag.*$|^t2flairsag.*$"
  )
  var seriesDescriptionRegexArray: Array[String] = _

  @Option(
    names = Array("-z", "--zip-depth"),
    arity = "1",
    description = Array("Depth of folders in file node tree to zip"),
    paramLabel = "ZIP_DEPTH",
    required = true,
    defaultValue = "1"
  )
  var zipDepth: String = _

  @Option(
    names = Array("-v", "--verbose"),
    description = Array("Verbose output"),
    paramLabel = "VERBOSE"
  )
  var verbose: Boolean = _

  @Option(
    names = Array("-t", "--print-file-trees"),
    description = Array("Print file trees"),
    paramLabel = "PRINT_FILE_TREES"
  )
  var printFileTrees: Boolean = _

  @Option(
    names = Array("-p", "--print-performance"),
    description = Array("Print performance"),
    paramLabel = "PRINT_PERFORMANCE"
  )
  var printPerformance: Boolean = _

  @throws(classOf[Exception])
  override def call(): Int = {

    // Capture start timestamp
    val startTS: Long = Calendar.getInstance().getTimeInMillis

    // Establish source and target Paths
    val sourceDirPath: Path = Paths.get(sourceDirStr)
    val targetDirPath: Path = Paths.get(targetDirStr)
    // Capture performance timestamp
    val definePathTS: Long = Calendar.getInstance().getTimeInMillis

    // Collapse CLI option arguments
    val intermedDirsRegex: String =
      sourceDirPath.getFileName.toString + "|" + intermedDirsRegexArray.mkString(sep = "|")
    val dicomFileRegex: String =
      dicomFileRegexArray.mkString(sep = "|")
    val seriesDescriptionRegex: String =
      seriesDescriptionRegexArray.mkString(sep = "|")
    // Capture performance timestamp
    val defineRegexTS: Long = Calendar.getInstance().getTimeInMillis

    // Build source directory node tree
    val sourceDirNode = DirNode(sourceDirPath, 0, intermedDirsRegex, dicomFileRegex)
    // Capture performance timestamp
    val sourceDirNodeTS: Long = Calendar.getInstance().getTimeInMillis

    // Build target directory node tree
    val targetDirNode = DirNode(targetDirPath, 0, intermedDirsRegex, dicomFileRegex)
    // Capture performance timestamp
    val targetDirNodeTS: Long = Calendar.getInstance().getTimeInMillis

    // Filter source directory node tree using CLI options and focused filters
    val sourceDirNodeFiltered: DirNode =
      sourceDirNode
        .filterChildDirNodesWith(intermedDirNameFilter(intermedDirsRegex)(_))
        .filterChildFileNodesWith(dicomFileFilter(dicomFileRegex)(_))
        .filterChildDirNodesWith(numberOfFilesFilter(210)(_))
        .filterChildFileNodesWith(dicomFileSeriesDescripFilter(seriesDescriptionRegex)(_))
        .filterChildDirNodesWith(nonemptyDirNodesFilter)
    // Capture performance timestamp
    val sourceDirNodeFilteredTS: Long = Calendar.getInstance().getTimeInMillis

    // Substitute target directory node tree root path with the source directory tree root path,
    // facilitating identification of source- and target-node tree discrepancies
    val targetDirNodeWithSourceRoot: DirNode =
    targetDirNode
      .substituteRootNodeName(
        targetDirNode.dirPath.getFileName.toString,
        sourceDirNode.dirPath.getFileName.toString
      )
    // Capture performance timestamp
    val targetDirNodeWithSourceRootTS: Long = Calendar.getInstance().getTimeInMillis

    // Filter source node directory tree based on files that already exists in target directory tree node
    val sourceDirNodeFilteredMinusTargetNodeWithSourceRoot =
      sourceDirNodeFiltered
        .filterNotChildFileNodesWith(childFileNodeExistsIn(targetDirNodeWithSourceRoot)(_))
        .filterChildDirNodesWith(nonemptyDirNodesFilter)
    // Capture performance timestamp
    val sourceDirNodeFilteredMinusTargetNodeWithSourceRootTS: Long = Calendar.getInstance().getTimeInMillis

    val sourceDirNodeFilteredMinusTargetNodeWithSourceRootWithTargetRoot: DirNode =
      sourceDirNodeFilteredMinusTargetNodeWithSourceRoot
        .substituteRootNodeName(
          sourceDirNode.dirPath.getFileName.toString,
          targetDirNode.dirPath.getFileName.toString
        )
    // Capture performance timestamp
    val sourceDirNodeFilteredMinusTargetNodeWithSourceRootWithTargetRootTS: Long =
      Calendar.getInstance().getTimeInMillis


    // TODO Create command-line options for controlling overwriting and attribute-copying
    val copyOptions: Seq[CopyOption] =
      Seq(REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS)
    // Create FileCopier object for copying directories and files
    val fileCopier = new FileCopier(sourceDirPath, targetDirPath, copyOptions, verbose)

    // Copy directories and files in filtered source directory node tree
    sourceDirNodeFilteredMinusTargetNodeWithSourceRoot.copyNode(fileCopier)
    // Capture performance timestamp
    val copyNodeTS: Long = Calendar.getInstance().getTimeInMillis

    // Zip directories and user-defined file node tree depth
    sourceDirNodeFilteredMinusTargetNodeWithSourceRootWithTargetRoot.zipNodesAtDepth(zipDepth.toInt, verbose)
    // Capture performance timestamp
    val zipNodeTS: Long = Calendar.getInstance().getTimeInMillis

    val endTS: Long = Calendar.getInstance().getTimeInMillis

    // Print file trees if CLI flag set
    if (printFileTrees) {
      println("targetDirNodeWithSourceRoot:")
      targetDirNodeWithSourceRoot.printNode()
      println()

      println("sourceDirNodeFilteredMinusTargetNodeWithSourceRoot:")
      sourceDirNodeFilteredMinusTargetNodeWithSourceRoot.printNode()
      println()
    }

    // Print performance times if CLI flag set
    if (printPerformance) {
      println("Performance (time in milliseconds)\n----------------------------------")
      println(s"Define source and target directory paths:   " +
        s"${definePathTS - startTS}")
      println(s"Define regex strings:                       " +
        s"${defineRegexTS - definePathTS}")
      println(s"Build source directory tree:                " +
        s"${sourceDirNodeTS - defineRegexTS}")
      println(s"Build target directory tree:                " +
        s"${targetDirNodeTS - sourceDirNodeTS}")
      println(s"Filter source directory tree:               " +
        s"${sourceDirNodeFilteredTS - targetDirNodeTS}")
      println(s"Replace target root with source root:       " +
        s"${targetDirNodeWithSourceRootTS - sourceDirNodeFilteredTS}")
      println(s"Subtract target tree from source tree:      " +
        s"${sourceDirNodeFilteredMinusTargetNodeWithSourceRootTS - targetDirNodeWithSourceRootTS}")
      println(s"Replace source root with target root:       " +
        s"${
          sourceDirNodeFilteredMinusTargetNodeWithSourceRootWithTargetRootTS -
            sourceDirNodeFilteredMinusTargetNodeWithSourceRootTS
        }")
      println(s"Copy only necessary nodes from source tree: " +
        s"${copyNodeTS - sourceDirNodeFilteredMinusTargetNodeWithSourceRootWithTargetRootTS}")
      println(s"Zip directories at user-defined depth:      " +
        s"${zipNodeTS - copyNodeTS}")
      println(s"                                            ------")
      println(s"Total runtime:                              ${endTS - startTS}")
    }

    0
  }
}

// ConstructTree companion object
object ConstructNodeTree extends App {
  val exitCode: Int = new CommandLine(new ConstructNodeTree()).execute(args: _*)
  System.exit(exitCode)
}
