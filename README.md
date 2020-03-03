# UmmapMriDeidentify App

## Using the App

Ensure that you have Java JRE 8 installed.

To run the app from a Bash command line, you need to know five pieces of information to pass with command flags:

1. `--source` (`-s`): Path to parent directory that holds all MRI directories and files.
2. `--target` (`-t`): Path to the Box JWT config file that authenticates the MADC Server Access App to interact with MADC Box Account files.
3. `--subdirs` (`-d`): Regex patterns for the intermediate subdirectories that lead to the directories that hold DICOM files. _**Regex patterns should be quoted!**_ (See [example run](https://gitlab.com/ldnicolasmay/ummapmrideidentify/#example-run) or [example run with logging](https://gitlab.com/ldnicolasmay/ummapmrideidentify/#example-run-with-logging) below.)
4. `--bottomdir` (`-b`): Regex pattern for the subdirectories that hold DICOM files. _**Regex patterns should be quoted!**_ (See [example run](https://gitlab.com/ldnicolasmay/ummapmrideidentify/#example-run) or [example run with logging](https://gitlab.com/ldnicolasmay/ummapmrideidentify/#example-run-with-logging) below.)
5. `--seriesdescription` (`-q`): Regex patterns for the MRI DICOM Series Descriptions in files that will be uploaded. _**Regex patterns should be quoted!**_ (See [example run](https://gitlab.com/ldnicolasmay/ummapmrideidentify/#example-run) or [example run with logging](https://gitlab.com/ldnicolasmay/ummapmrideidentify/#example-run-with-logging) below.)

There are four basic modes for this app:

1. The default mode is to simply upload directories and files that don't already exist, and to not preserve directory/file attributes (viz., last modified timestamp, owner, POSIX permissions).
2. If you pass the `--preserve` (`-p`) flag alone, the non-default behavior of preserving directory/file attributes of all _**new**_ directories/files will be enabled.
3. If you pass the `--overwrite` (`-o`) flag alone, the non-default behavior of overwriting all directories/files will be enabled. Including the `--overwrite` flag is, of course, a much more time-consuming run of the app.
4. If you pass both the `--preserve` (`-p`) and `--overwrite` (`-o`) flags, the combined non-default behavior of preserving all directory/file attributes _**and**_ overwriting all directories/files will be enabled. As mentioned above, including the `--overwrite` flag is a much more time-consuming run of the app.


### ~~Command Line Help~~ _**Not Implemented Yet**_

~~To see the command line help from a Bash prompt, run:~~

```
java -jar /path/to/UmmapDeidentify.jar --help
```

### Canonical Run

Here's an example of a canonical run including the `--preserve` and `--overwrite` flags from a Bash prompt:

```
java -jar /path/to/UmmapMriDeidentify.jar                   \
  --source SOURCE_PATH                                      \
  --target TARGET_PATH                                      \
  --subdirs SUBFOLDER_REGEX_1 SUBFOLDER_REGEX_2 ...         \
  --bottomdir BOTTOMDIR_REGEX                               \
  --seriesdescription SEQUENCE_REGEX_1 SEQUENCE_REGEX_2 ... \
  --preserve                                                \
  --overwrite
```

### Example Run

Here's an example run from a Bash prompt:

```
java -jar /path/to/UmmapMriDeidentify.jar                   \
  --source /path/to/source_mri_directory                    \
  --target /path/to/target_mri_directory                    \
  --subdirs "^hlp17umm\d{5}_\d{5}$" "^dicom$"               \
  --bottomdir "^s\d{5}$"                                    \
  --seriesdescription "^t1sag.*$" "^t2flairsag.*$" 
```

### ~~Example Run with Logging~~ _**Not Implemented Yet**_

~~For now, logging should be done with Bash redirect operator `>`:~~

```
java -jar /path/to/UmmapMriDeidentify.jar                   \
  --source /path/to/source_mri_directory                    \
  --target /path/to/target_mri_directory                    \
  --subdirs "^hlp17umm\d{5}_\d{5}$" "^dicom$"               \
  --bottomdir "^s\d{5}$"                                    \
  --seriesdescription "^t1sag.*$" "^t2flairsag.*$"          \
  --verbose > log/$(date "%Y-%m-%d_%H-%M-%S").log
```
