package dicomcopy;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.DirectoryStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.DirectoryNotEmptyException;
import java.io.IOException;

import com.pixelmed.dicom.*;

import static java.nio.file.FileVisitResult.*;
import static java.nio.file.StandardCopyOption.*;

/**
 * CopyFiles: Class for implementing FileVisitor to copy target directories and DICOM files
 *
 * @author L. D. Nicolas May
 * @version 0.1
 */
public class CopyFiles implements FileVisitor<Path> {

    private final Path source;
    private final Path target;
    private final String dicomSubDirs;
    private final String dicomBottomDir;
    private final String dicomSeriesDescrips;
    private final static String dicomFileRegex = "^i\\d+\\.MRDC\\.\\d+$";

    /**
     * CopyFiles constructor
     *
     * @param source              Path object of source directory
     * @param target              Path object of target directory
     * @param dicomSubDirs        String object of regex for allowable sub-directories
     * @param dicomBottomDir      String object of regex for directories that hold DICOM files
     * @param dicomSeriesDescrips String object of regex for which DICOM sequence series descriptions to include
     */
    CopyFiles(Path source, Path target,
              String dicomSubDirs, String dicomBottomDir, String dicomSeriesDescrips) {
        this.source = source;
        this.target = target;
        this.dicomSubDirs = dicomSubDirs;
        this.dicomBottomDir = dicomBottomDir;
        this.dicomSeriesDescrips = dicomSeriesDescrips;
    }

    /**
     * Method for copying a source Path object to a destination Path
     *
     * @param source Path object of source directory
     * @param target Path object of target directory
     * @return FileVisitResult action
     */
    private static FileVisitResult copyFile(Path source, Path target) {

        try {
            Files.copy(source, target, COPY_ATTRIBUTES, REPLACE_EXISTING);
            // System.out.format("Copy dir:%n  src: %s%n  trg: %s%n", source.toString(), target.toString());
        } catch (FileAlreadyExistsException | DirectoryNotEmptyException e) {
            // ignore
        } catch (IOException e) {
            System.err.format("Unable to copy: %s: %s%n", source, e);
            return SKIP_SUBTREE;
        }
        return CONTINUE;
    }

    /**
     * Method to extract DICOM sequence series description from a file Path object
     *
     * @param file Path object of DICOM file
     * @return String of DICOM sequence series description
     */
    private static String getSeriesDescriptionFromPath(Path file) {

        AttributeList attrList = new AttributeList();
        String seriesDescription = null;

        if (file.getFileName().toString().matches(dicomFileRegex)) {
            try {
                attrList.read(file.toString());
                seriesDescription =
                        Attribute.getDelimitedStringValuesOrEmptyString(attrList, TagFromName.SeriesDescription);
            } catch (DicomException e) {
                System.err.format("%s %s%n", file.toString(), e);
            } catch (IOException e) {
                System.err.format("IOException: %s%n", e);
            }
        }
        return seriesDescription;
    }

    /**
     * Method that implements DirectoryStream.Filter interface for filtering out DICOM files that do NOT match
     * the provided sequence series description regex
     */
    private final DirectoryStream.Filter<Path> filterT1T2 = new DirectoryStream.Filter<Path>() {

        @Override
        public boolean accept(Path file) {
            if (file.getFileName().toString().matches(dicomFileRegex)) {
                String seriesDescription = getSeriesDescriptionFromPath(file);
                return seriesDescription.matches(dicomSeriesDescrips);
            }
            return false;
        }
    };

    /**
     * Print information about a directory or file being copied
     *
     * @param source Source Path object (directory or file)
     * @param target Target Path object (directory or file)
     */
    private static void printCopyFile(Path source, Path target) {
        if (Files.isDirectory(source)) {
            System.out.format("Copy dir:%n  src: %s%n  trg: %s%n", source.toString(), target.toString());
        } else if (Files.isRegularFile(source)) {
            System.out.format("Copy file:%n  src: %s%n  trg: %s%n", source.toString(), target.toString());
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
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) {

        Path targetDir = target.resolve(source.relativize(dir));
        String sourceDirString = source.getFileName().toString();
        boolean targetDirIsIntermedDicomDir = false;
        boolean targetDirIsBottomDicomDir = false;
        boolean targetDirHasRightDicoms = false;

        if (dir.getFileName().toString().matches(sourceDirString)) {
            targetDirIsIntermedDicomDir = true;
        } else if (dir.getFileName().toString().matches(dicomSubDirs)) {
            targetDirIsIntermedDicomDir = true;
        }

        // If at a bottom DICOM directory, check that its DICOM files have series descriptions that match passed regex
        if (dir.getFileName().toString().matches(dicomBottomDir)) {
            targetDirIsBottomDicomDir = true;
            // Filter dir's contents for only DICOM files with target sequence series description(s)
            try (DirectoryStream<Path> filteredStream = Files.newDirectoryStream(dir, filterT1T2)) {
                if (filteredStream.iterator().hasNext()) {  // if there's anything in the filteredStream
                    targetDirHasRightDicoms = true;
                }
            } catch (IOException e) {
                System.err.format("IOException: %s%n", e);
            }
        }

        if (targetDirIsIntermedDicomDir) {
            // printCopyFile(dir, targetDir);
            return copyFile(dir, targetDir);
        } else if (targetDirIsBottomDicomDir && targetDirHasRightDicoms) {
            // printCopyFile(dir, targetDir);
            return copyFile(dir, targetDir);
        }

        return SKIP_SUBTREE;
    }

    /**
     * Override method of FileVisitor for actions to perform after visiting a directory Path
     *
     * @param dir Source directory Path
     * @param e   IOException thrown on error
     * @return FileVisitResult action
     */
    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException e) {

        if (e != null) {
            System.err.format("Unable to copy dir2: %s: %s%n", dir, e);
        }
        return CONTINUE;
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
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {

        Path targetFile = target.resolve(source.relativize(file));

        if (targetFile.getFileName().toString().matches(dicomFileRegex)) {
            // printCopyFile(file, targetFile);
            copyFile(file, targetFile);
        }
        return CONTINUE;
    }

    /**
     * Override method of FileVisitor for actions to perform upon a failed file Path visit
     *
     * @param file Source file Path
     * @param e    IOException thrown on error
     * @return FileVisitResult action
     */
    @Override
    public FileVisitResult visitFileFailed(Path file, IOException e) {

        System.err.format("Unable to copy file: %s: %s%n", file, e);
        return CONTINUE;
    }

}
