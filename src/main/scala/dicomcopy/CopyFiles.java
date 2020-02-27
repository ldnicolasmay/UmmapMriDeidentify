package dicomcopy;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;

import com.pixelmed.dicom.*;

import static java.nio.file.FileVisitResult.*;
import static java.nio.file.StandardCopyOption.*;

public class CopyFiles implements FileVisitor<Path> {

    private final Path source;
    private final Path target;
    private final String[] dicomSubDirs;
    private final String dicomBottomDir;

    CopyFiles(Path source, Path target, String[] dicomSubDirs, String dicomBottomDir) {
        this.source = source;
        this.target = target;
        this.dicomSubDirs = dicomSubDirs;
        this.dicomBottomDir = dicomBottomDir;
    }

    static FileVisitResult copyFile(Path source, Path target) {
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

    private final DirectoryStream.Filter<Path> filterT1T2 = new DirectoryStream.Filter<Path>() {
        public boolean accept(Path file) throws IOException {
            AttributeList list = new AttributeList();
            try {
                if (file.getFileName().toString().startsWith("i")) {
                    list.read(file.toString());
                    String seriesDescription =
                            Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.SeriesDescription);
                    return (seriesDescription.startsWith("t1sag") || seriesDescription.startsWith("t2flairsag"));
                } else {
                    return false;
                }
            } catch (DicomException e) {
                System.err.format("DicomException: %s%n", e);
                return false;
            }
        }
    };

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) {
        Path targetDir = target.resolve(source.relativize(dir)); // ensures clean Path object

        String sourceDirString = source.getFileName().toString();
        boolean targetDirMatchesRegexes = false;
        boolean targetDirIsBottomDicomDir = false;
        boolean targetDirHasRightDicoms = false;
        String seriesDescription;

        if (dir.getFileName().toString().matches(sourceDirString)) {
            targetDirMatchesRegexes = true;
        } else if (dir.getFileName().toString().matches(dicomBottomDir)) {
            targetDirMatchesRegexes = true;
        } else {
            for (String subDir : dicomSubDirs) {
                if (dir.getFileName().toString().matches(subDir)) {
                    targetDirMatchesRegexes = true;
                }
            }
        }

        if (dir.getFileName().toString().matches(dicomBottomDir)) {
            targetDirIsBottomDicomDir = true;
        }

        try (DirectoryStream<Path> filteredStream = Files.newDirectoryStream(dir, filterT1T2)) {
            for (Path filteredStreamFile : filteredStream) {
                seriesDescription = getSeriesDescriptionFromPath(filteredStreamFile);
                if (seriesDescription != null) {
                    if (seriesDescription.startsWith("t1sag") || seriesDescription.startsWith("t2flairsag")) {
                        targetDirHasRightDicoms = true;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.format("IOException: %s%n", e);
        }

        if (targetDirMatchesRegexes) {
            if (!targetDirIsBottomDicomDir) {
                return copyFile(dir, targetDir);
            } else if (targetDirHasRightDicoms) {
                return copyFile(dir, targetDir);
            }
        }
        return SKIP_SUBTREE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException e) {
        if (e != null) {
            System.err.format("Unable to copy dir2: %s: %s%n", dir, e);
        }
        return CONTINUE;
    }

    static String getSeriesDescriptionFromPath(Path file) {

        AttributeList attrList = new AttributeList();
        String seriesDescription = null;

        if (file.getFileName().toString().startsWith("i")) {
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

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {

        Path targetFile = target.resolve(source.relativize(file)); // ensures clean Path object
        String dicomFileRegex = "^i\\d+\\.MRDC\\.\\d+$";

        // if (targetFile.getFileName().toString().startsWith("i")) {
        if (targetFile.getFileName().toString().matches(dicomFileRegex)) {
            try {
                Files.copy(file, targetFile, COPY_ATTRIBUTES, REPLACE_EXISTING);
                // System.out.format("Copy file:%n  src: %s%n  trg: %s%n", file.toString(), targetFile.toString());
            } catch (IOException e) {
                System.err.format("Unable to copy: %s: %s%n", file, e);
            }
        }

//        String seriesDescription = getSeriesDescriptionFromPath(file);
//        if (seriesDescription != null) {
//            if (seriesDescription.startsWith("t1sag") || seriesDescription.startsWith("t2flairsag")) {
//                try {
//                    Files.copy(file, targetFile, COPY_ATTRIBUTES, REPLACE_EXISTING);
//                     System.out.format("Copy file:%n  src: %s%n  trg: %s%n",
//                             file.toString(), targetFile.toString());
//                } catch (IOException e) {
//                    System.err.format("Unable to copy: %s: %s%n", file, e);
//                }
//            }
//        }

        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException e) {
        System.err.format("Unable to copy file: %s: %s%n", file, e);
        return CONTINUE;
    }

}
