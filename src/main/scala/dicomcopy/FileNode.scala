package dicomcopy

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.IteratorHasAsScala


case class FileNode(filePath: Path, depth: Int) extends Node {

  /**
   * TODO Write method description
   */
  override def printNode(): Unit =
    println(s"${"  " * depth}$depth : ${filePath.toString}")

  /**
   * TODO Write method description
   *
   * @param fileCopier TODO
   */
  override def copyNode(fileCopier: FileCopier): Unit = {
    val attr = Files.readAttributes(filePath, classOf[BasicFileAttributes])
    try {
      fileCopier.visitFile(filePath, attr)
    } catch {
      case e: IOException => fileCopier.visitFileFailed(filePath, e)
    }
  }

  /**
   * TODO Write method description
   *
   * @param name TODO
   * @return TODO
   */
  override def getSubpathIndexOf(name: String): Int = {
    val fileNodeFileNameSeq: Seq[String] =
      filePath
        .iterator()
        .asScala
        .map(_.getFileName.toString)
        .toSeq
    fileNodeFileNameSeq.indexOf(name)
  }

  /**
   * TODO Write method description
   *
   * @return TODO
   */
  override def getPathLength: Int = {
    filePath
      .iterator()
      .asScala
      .length
  }

  /**
   * TODO Write method description
   *
   * @param oldName TODO
   * @param newName TODO
   * @return TODO
   */
  override def substituteRootNodeName(oldName: String, newName: String): FileNode = {
    val nameIndex: Int = this.getSubpathIndexOf(oldName)
    val newPath =
      filePath
        .getRoot
        .resolve(
          filePath
            .subpath(0, nameIndex)
            .resolve(newName)
            .resolve(filePath.subpath(nameIndex + 1, this.getPathLength))
        )
    FileNode(newPath, depth)
  }

}
