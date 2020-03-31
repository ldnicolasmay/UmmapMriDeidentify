package dicomcopy


abstract class Node {

  /**
   * TODO Write method description
   */
  def printNode(): Unit

  /**
   * TODO Write method description
   *
   * @param fc TODO
   */
  def copyNode(fc: FileCopier): Unit

  /**
   * TODO Write method description
   *
   * @param name
   * @return TODO
   */
  def getSubpathIndexOf(name: String): Int

  /**
   * TODO Write method description
   *
   * @return TODO
   */
  def getPathLength: Int

  /**
   * TODO Write method description
   *
   * @param oldName TODO
   * @param newName TODO
   * @return TODO
   */
  def substituteRootNodeName(oldName: String, newName: String): Node

}
