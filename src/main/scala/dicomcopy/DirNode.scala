package dicomcopy

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{DirectoryStream, Files, Path, Paths}

import scala.util.{Failure, Success, Try, Using}
import scala.jdk.CollectionConverters.IteratorHasAsScala

import org.zeroturnaround.zip.{ZipUtil, NameMapper}


/**
 * Class for recursively constructed directory nodes
 *
 * @param dirPath           Path of directory
 * @param depth             Int depth of DirNode object in Node tree containing it
 * @param childDirNodes     List of child DirNode objects
 * @param childFileNodes    List of child FileNode objects
 * @param intermedDirsRegex String regex of intermediate directories leading to or containing DICOM files
 * @param dicomFileRegex    String regex of DICOM file names
 */
case class DirNode(
                    dirPath: Path,
                    depth: Int,
                    childDirNodes: List[DirNode],
                    childFileNodes: List[FileNode],
                    intermedDirsRegex: String,
                    dicomFileRegex: String
                  )
  extends Node {

  /**
   * Return DirNode object in this node tree whose path string matches passed `dirPathString`
   *
   * @param dirPathString String path to match DirNode object in this node tree
   * @return DirNode object of interest
   */
  def findDirNode(dirPathString: String): Option[DirNode] =
    childDirNodes.find(_.dirPath.toString == dirPathString)

  /**
   * Return FileNode object in this node tree whose path string matches passed `filePathString`
   *
   * @param filePathString String path to match FileNode object in this DirNode tree
   * @return FileNode object of interest
   */
  def findFileNode(filePathString: String): Option[FileNode] =
    childFileNodes.find(_.filePath.toString == filePathString)

  /**
   * Print hierarchical representation of this DirNode tree
   */
  override def printNode(): Unit = {
    println(s"${"  " * depth}$depth ${dirPath.toString}")
    childDirNodes.foreach(_.printNode())
    childFileNodes.foreach(_.printNode())
  }

  /**
   * String hierarchical representation of this DirNode tree
   *
   * @return String of DirNode tree represented hierarchically
   */
  override def toString: String = {
    s"$depth ${dirPath.toString}" + "\n" +
      childDirNodes.map(_.toString) + "\n" +
      childFileNodes.map(_.toString) + "\n"
  }

  /**
   * Recursively count number of child Node objects beneath this DirNode object
   *
   * @return Int of Node count
   */
  def countSubNodes(): Int = {
    val nodeCountInChildDirs =
      childDirNodes
        .map(_.countSubNodes())
        .sum
    childDirNodes.length + childFileNodes.length + nodeCountInChildDirs
  }

  /**
   * Recursively filter the child DirNode objects of this DirNode object based on passed predicate
   *
   * @param predicate Function that accepts a DirNode object and returns a Boolean
   * @return DirNode object filtered
   */
  def filterChildDirNodesWith(predicate: DirNode => Boolean): DirNode = {
    val filteredChildDirs =
      childDirNodes
        .map(_.filterChildDirNodesWith(predicate))
        .filter(predicate)
    this.copy(dirPath, depth, filteredChildDirs, childFileNodes)
  }

  /**
   * Recursively negate-filter the child DirNode objects of this DirNode object based on passed predicate
   *
   * @param predicate Function that accepts a DirNode object and returns a Boolean
   * @return DirNode object filtered
   */
  def filterNotChildDirNodesWith(predicate: DirNode => Boolean): DirNode = {
    val filteredChildDirs =
      childDirNodes
        .map(_.filterChildDirNodesWith(predicate))
        .filterNot(predicate)
    this.copy(dirPath, depth, filteredChildDirs, childFileNodes)
  }

  /**
   * Recursively filter the child FileNode objects of this DirNode object based on passed predicate
   *
   * @param predicate Function that accepts a FileNode object and returns a Boolean
   * @return DirNode object filtered
   */
  def filterChildFileNodesWith(predicate: FileNode => Boolean): DirNode = {
    val filteredChildFiles = childFileNodes.filter(predicate)
    val filteredChildDirs = childDirNodes.map(_.filterChildFileNodesWith(predicate))
    this.copy(dirPath, depth, filteredChildDirs, filteredChildFiles)
  }

  /**
   * Recursively negate-filter the child FileNode objects of this DirNode object based on passed predicate
   *
   * @param predicate Function that accepts a FileNode object and returns a Boolean
   * @return DirNode object filtered
   */
  def filterNotChildFileNodesWith(predicate: FileNode => Boolean): DirNode = {
    val filteredChildFiles = childFileNodes.filterNot(predicate)
    val filteredChildDirs = childDirNodes.map(_.filterNotChildFileNodesWith(predicate))
    this.copy(dirPath, depth, filteredChildDirs, filteredChildFiles)
  }

  /**
   * Recursively copy this DirNode tree's directories and files using passed FileCopier object; side effects only
   *
   * @param fileCopier FileCopier object with requisite source path, target path, copy options, verbose flag
   */
  override def copyNode(fileCopier: FileCopier): Unit = {
    val file = dirPath
    val attr = Files.readAttributes(file, classOf[BasicFileAttributes])
    var exc: IOException = null
    try {
      fileCopier.preVisitDirectory(file, attr)
    } catch {
      case e: IOException => exc = e
    }
    childDirNodes.foreach(_.copyNode(fileCopier))
    childFileNodes.foreach(_.copyNode(fileCopier))
    fileCopier.postVisitDirectory(file, exc)
  }

  /**
   * Zip directories at user-defined depth of this DirNode tree
   *
   * @param zipDepth Depth in this DirNode tree to zip directories
   */
  def zipNodesAtDepth(zipDepth: Int, verbose: Boolean): Unit = {
    if (this.depth <= zipDepth) {
      if (this.depth < zipDepth) {
        // recurse down
        childDirNodes.foreach(_.zipNodesAtDepth(zipDepth, verbose))
      }
      else {
        // zip this dir
        if (verbose) println(s"Zipping ${dirPath.toString} @ depth $depth")
        val zipPath: Path = Paths.get(dirPath.toString + ".zip")
        ZipUtil.pack(
          dirPath.toFile,
          zipPath.toFile,
          new NameMapper() {
            override def map(name: String): String = dirPath.getFileName.toString + "/" + name
          }
        )
      }
    }
    else ()
  }

  /**
   * Get path index of the directory or file name String passed to method
   *
   * For example, DirNode("/foo/bar").getSubpathIndexOf("foo") returns 0
   *
   * @param name String name of directory or file to get the path index of
   * @return Int index of directory or file
   */
  override def getSubpathIndexOf(name: String): Int = {
    val dirNodeFileNameSeq: Seq[String] =
      dirPath
        .iterator()
        .asScala
        .map(_.getFileName.toString)
        .toSeq
    dirNodeFileNameSeq.indexOf(name)
  }

  /**
   * Get length of a this DirNode's path iterator, effectively a count of the directories in this path
   *
   * For example, DirNode("/foo/bar").getPathLength returns 2
   *
   * @return Int length of this DirNode object's iterator
   */
  override def getPathLength: Int =
    dirPath.iterator().asScala.length

  /**
   * Substitute a path string for this DirNode object's path string
   *
   * For example,
   * DirNode("/target/path/file.txt").substituteRootNodeName("/target/path/file.txt", "/source/path/file.txt")
   * returns DirNode("/source/path/file.txt")
   *
   * @param oldName Old path String to substitute
   * @param newName New path String to use
   * @return DirNode object with new substituted path
   */
  override def substituteRootNodeName(oldName: String, newName: String): DirNode = {
    val nameIndex: Int = getSubpathIndexOf(oldName)
    val pathLength: Int = getPathLength
    val newPath: Path =
      if (pathLength <= nameIndex + 1) {
        dirPath.getRoot.resolve(
          dirPath
            .subpath(0, nameIndex)
            .resolve(newName)
        )
      } else {
        dirPath.getRoot.resolve(
          dirPath
            .subpath(0, nameIndex)
            .resolve(newName)
            .resolve(dirPath.subpath(nameIndex + 1, pathLength))
        )
      }
    val newChildFileNodes: List[FileNode] =
      childFileNodes.map(_.substituteRootNodeName(oldName, newName))
    val newChildDirNodes: List[DirNode] =
      childDirNodes.map(_.substituteRootNodeName(oldName, newName))

    DirNode(
      newPath,
      depth,
      newChildDirNodes,
      newChildFileNodes,
      intermedDirsRegex,
      dicomFileRegex
    )
  }

}

/**
 * Companion object for DirNode class
 */
object DirNode {

  /**
   * Apply method to build DirNode object from partial parameters
   *
   * @param dirPath           Path of directory
   * @param depth             Int depth of DirNode object in Node tree containing it
   * @param intermedDirsRegex String regex of intermediate directories leading to or containing DICOM files
   * @param dicomFileRegex    String regex of DICOM file names
   * @return DirNode object
   */
  def apply(dirPath: Path,
            depth: Int,
            intermedDirsRegex: String,
            dicomFileRegex: String
           ): DirNode = {

    /**
     * Method that implements DirectoryStream.Filter interface for keeping directories and DICOM files
     */
    val dirDicomFileFilter = new DirectoryStream.Filter[Path]() {
      override def accept(t: Path): Boolean = {
        try {
          (Files.isDirectory(t) && t.getFileName.toString.matches(intermedDirsRegex)) ||
            (Files.isRegularFile(t) && t.getFileName.toString.matches(dicomFileRegex))
        } catch {
          case e: IOException => System.err.println(e)
            false
        }
      }
    }

    val children: Try[(List[DirNode], List[FileNode])] =
      Using.Manager {
        use =>
          // Use one Java Directory Stream
          val dirStream: DirectoryStream[Path] =
            use(Files.newDirectoryStream(dirPath, dirDicomFileFilter))
          // Partition DirectoryStream into tuple of Path Iterators
          val partitionedDirStream: (Iterator[Path], Iterator[Path]) = dirStream
            .iterator()
            .asScala
            .partition(Files.isDirectory(_))
          // Get the child directory Path Iterator from the tuple; Map each directory Path into a DirNode
          val childDirNodesIt: Iterator[DirNode] =
            partitionedDirStream._1
              .map(DirNode(_, depth + 1, intermedDirsRegex, dicomFileRegex))
          // Get the child file Path iterator from the tuple; Map each file Path into a FileNode
          val childFileNodesIt: Iterator[FileNode] =
            partitionedDirStream._2
              .map(FileNode(_, depth + 1))
          // Convert children iterators to List at last possible moment
          (childDirNodesIt.toList, childFileNodesIt.toList)
      }

    val derivedChildDirNodes: List[DirNode] =
      children match {
        case Success(c) => c._1
        case Failure(_) => List()
      }

    val derivedChildFileNodes: List[FileNode] =
      children match {
        case Success(c) => c._2
        case Failure(_) => List()
      }

    this (
      dirPath,
      depth,
      derivedChildDirNodes,
      derivedChildFileNodes,
      intermedDirsRegex,
      dicomFileRegex
    )
  }

}
