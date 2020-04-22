# UmmapMriDeidentify App

This app copies and deïdentifies DICOM files from a well defined source directory tree to a target directory. To improve efficiency, it compares the user-defined source directory tree to the existing target directory tree and processes only those files that do not yet exist in the target directory tree.

A firm grasp of regular expressions is required to use this app effectively. ([RegexOne](https://regexone.com/) offers a good tutorial on regular expressions.)

_**Regex patterns passed as command-line option arguments should be quoted!**_ See [example run](https://gitlab.com/ldnicolasmay/ummapmrideidentify/-/tree/master#example-run) or [example run with logging](https://gitlab.com/ldnicolasmay/ummapmrideidentify/-/tree/master#example-run-with-logging) below.

## Using the App

Ensure that you have Java JRE 8 installed.

To run the app from a Bash command line, you need to know five pieces of information to pass as arguments or options+arguments:

1. Source argument: Path of parent directory that holds all DICOM directories and files.
2. Target argument: Path of parent directory to which filtered DICOM directories and files will be copied and deïdentified.
3. `--intermed-dirs-regex` option (`-i`): Regex pattern(s) for the intermediate subdirectory name(s) that _lead to or contain_ DICOM files. 
4. `--dicom-file-regex` option (`-d`): Regex pattern(s) for the DICOM file name(s).
5. `--series-description-regex` option (`-s`): Regex pattern(s) for the DICOM Series Description string(s) of DICOM files to be uploaded.

There's an option to zip directories at a user-defined directory tree depth. The zip archive files (`.zip`) will be created at the same level as the directories that are zipped.

1. `--zip-depth` option (`-z`): Integer depth at which to create zip archives (`.zip`) within the target directory tree. For example, `--zip-depth 0` will zip the entire target directory tree ("Target argument" above) into one large zip archive; `--zip-depth 1` will zip each directory 1 level under the target directory.

There are also options for printing output:

1. `--verbose` option (`-v`): Prints source and target paths for each directory and file copied; handy for simple logging.
2. `--print-file-trees` option (`-t`): Prints trees of the source directory tree and the directory tree to be copied and deïdentified. This should only be used for testing on small, subsetted directory trees.
3. `--print-performance` option (`-p`): Prints performance of app run.

### Command Line Help

To see the command line help from a Bash prompt, run:

```
java -jar /path/to/UmmapDeidentify.jar --help
```

### Canonical Run

#### Without Zipped Directories

Here's an example of a canonical run from a Bash prompt without any zipping:

```
java -jar /path/to/UmmapMriDeidentify.jar                                              \
  SOURCE_DIR                                                                           \
  TARGET_DIR                                                                           \
  --intermed-dirs-regex      INTERMED_DIRS_REGEX_1 INTERMED_DIRS_REGEX_2 ...           \
  --dicom-file-regex         DICOM_FILE_REGEX_1 DICOM_FILE_REGEX_2 ...                 \
  --series-description-regex SERIES_DESCRIPTION_REGEX_1 SERIES_DESCRIPTION_REGEX_2 ...
```

#### With Zipped Directories

Here's an example of a canonical run from a Bash prompt with zipping of directories at depth 1 of target directory tree:

```
java -jar /path/to/UmmapMriDeidentify.jar                                              \
  SOURCE_DIR                                                                           \
  TARGET_DIR                                                                           \
  --intermed-dirs-regex      INTERMED_DIRS_REGEX_1 INTERMED_DIRS_REGEX_2 ...           \
  --dicom-file-regex         DICOM_FILE_REGEX_1 DICOM_FILE_REGEX_2 ...                 \
  --series-description-regex SERIES_DESCRIPTION_REGEX_1 SERIES_DESCRIPTION_REGEX_2 ... \
  --zip-depth                ZIP_DEPTH
```

### Example Run

Here's an example run from a Bash prompt with zipping of directories at depth 1 of target directory tree:

```
java -jar /path/to/UmmapMriDeidentify.jar                            \
  /path/to/source_mri_directory                                      \
  /path/to/target_mri_directory                                      \
  --intermed-dirs-regex "^hlp17umm\d{5}_\d{5}$" "^dicom$" "^s\d{5}$" \
  --dicom-file-regex    "^i\d+\.MRDC\.\d+$"                          \
  --seriesdescription   "^t1sag.*$" "^t2flairsag.*$"                 \
  --zip-depth           1
```

### Example Run with Logging

For now, logging can be done with Bash redirect operator `>`:

```
java -jar /path/to/UmmapMriDeidentify.jar                            \
  /path/to/source_mri_directory                                      \
  /path/to/target_mri_directory                                      \
  --intermed-dirs-regex "^hlp17umm\d{5}_\d{5}$" "^dicom$" "^s\d{5}$" \
  --dicom-file-regex    "^i\d+\.MRDC\.\d+$"                          \
  --seriesdescription   "^t1sag.*$" "^t2flairsag.*$"                 \
  --zip-depth           1                                            \
  --verbose                                                          \
  1>log/$(date +"%Y-%m-%d_%H-%M-%S").log                             \
  2>err/$(date +"%Y-%m-%d_%H-%H-%S").err
```
