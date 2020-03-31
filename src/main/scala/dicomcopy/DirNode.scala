package dicomcopy

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{DirectoryStream, Files, Path}
import scala.util.{Failure, Success, Try, Using}
import scala.jdk.CollectionConverters.IteratorHasAsScala


/**
 * TODO Write class description
 *
 * @param dirPath TODO
 * @param depth TODO
 * @param childDirNodes TODO
 * @param childFileNodes TODO
 * @param intermedDirsRegex TODO
 * @param dicomFileRegex TODO
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
   * TODO Write method description
   *
   * @param dirPathString TODO
   * @return TODO
   */
  def findDirNode(dirPathString: String): Option[DirNode] = {
    childDirNodes.find(_.dirPath.toString == dirPathString)
  }

  /**
   * TODO Write method description
   *
   * @param filePathString TODO
   * @return TODO
   */
  def findFileNode(filePathString: String): Option[FileNode] = {
    childFileNodes.find(_.filePath.toString == filePathString)
  }

  /**
   * TODO Write method description
   */
  override def printNode(): Unit = {
    println(s"${"  " * depth}$depth ${dirPath.toString}")
    childDirNodes.foreach(_.printNode())
    childFileNodes.foreach(_.printNode())
  }

  /**
   * TODO Write method description
   *
   * @return TODO
   */
  def countSubNodes(): Int = {
    val nodeCountInChildDirs =
      childDirNodes
        .map(_.countSubNodes())
        .sum
    childDirNodes.length + childFileNodes.length + nodeCountInChildDirs
  }

  /**
   * TODO Write method description
   *
   * @param predicate TODO
   * @return TODO
   */
  def filterChildDirNodesWith(predicate: DirNode => Boolean): DirNode = {
    val filteredChildDirs =
      childDirNodes
        .map(_.filterChildDirNodesWith(predicate))
        .filter(predicate)
    this.copy(dirPath, depth, filteredChildDirs, childFileNodes)
  }

  /**
   * TODO Write method description
   *
   * @param predicate TODO
   * @return TODO
   */
  def filterNotChildDirNodesWith(predicate: DirNode => Boolean): DirNode = {
    val filteredChildDirs =
      childDirNodes
        .map(_.filterChildDirNodesWith(predicate))
        .filterNot(predicate)
    this.copy(dirPath, depth, filteredChildDirs, childFileNodes)
  }

  /**
   * TODO Write method description
   *
   * @param predicate TODO
   * @return TODO
   */
  def filterChildFileNodesWith(predicate: FileNode => Boolean): DirNode = {
    val filteredChildFiles = childFileNodes.filter(predicate)
    val filteredChildDirs = childDirNodes.map(_.filterChildFileNodesWith(predicate))
    this.copy(dirPath, depth, filteredChildDirs, filteredChildFiles)
  }

  /**
   * TODO Write method description
   *
   * @param predicate TODO
   * @return TODO
   */
  def filterNotChildFileNodesWith(predicate: FileNode => Boolean): DirNode = {
    val filteredChildFiles = childFileNodes.filterNot(predicate)
    val filteredChildDirs = childDirNodes.map(_.filterNotChildFileNodesWith(predicate))
    this.copy(dirPath, depth, filteredChildDirs, filteredChildFiles)
  }

  /**
   * TODO Write method description
   *
   * @param fileCopier TODO
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
   * TODO Write method description
   *
   * @param name TODO
   * @return TODO
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
   * TODO Write method description
   *
   * @return TODO
   */
  override def getPathLength: Int = {
    dirPath.iterator().asScala.length
  }

  /**
   * TODO Write method description
   *
   * @param oldName TODO
   * @param newName TODO
   * @return TODO
   */
  override def substituteRootNodeName(oldName: String, newName: String): DirNode = {
    val nameIndex: Int = getSubpathIndexOf(oldName)
    val pathLength = getPathLength
    val newPath =
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
    val newChildFileNodes =
      childFileNodes.map(_.substituteRootNodeName(oldName, newName))
    val newChildDirNodes =
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

object DirNode {

  /**
   * TODO Write method description
   *
   * @param dirPath TODO
   * @param depth   TODO
   * @return TODO
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
