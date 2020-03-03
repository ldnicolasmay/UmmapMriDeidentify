package dicomcopy

import org.apache.commons.cli.{HelpFormatter, Option, Options}


private[dicomcopy] object CliOptions {

  // Define command line options
  val options: Options = new Options

  //  // Add help option
  //  private val helpOpt: Option = Option.builder("h")
  //    .longOpt("help")
  //    .desc("print help")
  //    .build
  //  options.addOption(helpOpt)

  // Add source directory option
  private val dirSourceOpt: Option = Option.builder("s")
    .required
    .longOpt("source")
    .desc("source directory")
    .hasArg
    .argName("SOURCEDIR")
    .build
  options.addOption(dirSourceOpt)

  // Add target directory option
  private val dirTargetOpt: Option = Option.builder("t")
    .required
    .longOpt("target")
    .desc("target directory")
    .hasArg
    .argName("TARGETDIR")
    .build
  options.addOption(dirTargetOpt)

  //  Add dicom sub-directories option
  private val dicomSubDirsOpt: Option = Option.builder("d")
    .required
    .longOpt("subdirs")
    .desc("dicom subdirectories regex strings")
    .hasArgs
    .argName("DICOMSUBDIRS")
    .build
  options.addOption(dicomSubDirsOpt)

  // Add dicom bottom directory option
  private val dicomBottomDirOpt: Option = Option.builder("b")
    .required
    .longOpt("bottomdir")
    .desc("dicom bottom directory regex string")
    .hasArg
    .argName("DICOMBOTTOMDIR")
    .build
  options.addOption(dicomBottomDirOpt)

  // Add DICOM sequence series description option
  private val dicomSeriesDescripOpt: Option = Option.builder("q")
    .required
    .longOpt("seriesdescription")
    .desc("dicom sequence series description regex strings")
    .hasArgs
    .argName("DICOMSERIESDESCRIPTION")
    .build
  options.addOption(dicomSeriesDescripOpt)

  // Add boolean flag for preserving directory/file attributes
  private val preserveOpt =
    new Option("p", "preserve", false, "preserve directory/file attributes")
  options.addOption(preserveOpt)

  // Add boolean flag for overwiting existing directories/files
  private val overwriteOpt =
    new Option("o", "overwrite", false, "overwrite preexisting directories/files")
  options.addOption(overwriteOpt)

  // private val formatter: HelpFormatter = new HelpFormatter
  // private val header: String = "Copy and deidentify DICOM files\n\n"
  // private val footer: String = "\nPlease report issues to madc-data@umich.edu"
  // formatter.printHelp("java -jar UmmapMriDeidentify.jar", header, options, footer, true)
  // formatter.printHelp("java -jar UmmapMriDeidentify.jar", options)

}
