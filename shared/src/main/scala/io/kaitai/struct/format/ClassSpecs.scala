package io.kaitai.struct.format

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Top-level abstract container for all ClassSpecs. Used for recursive
  * loading of imports. Real-life implementation depend on file handling
  * (which at least differ between JVM vs JS), and thus implementations
  * are platform-dependent.
  */
abstract class ClassSpecs(val firstSpec: ClassSpec) extends mutable.HashMap[String, ClassSpec] {
  this(firstSpec.name.head) = firstSpec

  /**
    * Calls certain function on all [[ClassSpec]] elements stored in this ClassSpecs,
    * and all subtypes stored in these elements, recursively.
    *
    * @param proc function to execute on every encountered type.
    */
  def forEachRec(proc: (ClassSpec) => Unit): Unit =
    forEachTopLevel((_, typeSpec) => typeSpec.forEachRec(proc))

  /**
    * Calls certain function on all top-level [[ClassSpec]] elements stored in this
    * ClassSpecs.
    */
  def forEachTopLevel[R](proc: (String, ClassSpec) => Unit): Unit = {
    foreach { case (specName, typeSpec) =>
//      try {
        proc(specName, typeSpec)
//      } catch {
        // TODO: try to emit more specific error, with a reference to current file
//      }
    }
  }

  /**
    * Calls certain function on all [[ClassSpec]] elements stored in this ClassSpecs,
    * and all subtypes stored in these elements, recursively.
    *
    * @param proc function to execute on every encountered type.
    * @tparam R mandates that function must return a list of this type.
    */
  def mapRec[R](proc: (ClassSpec) => Iterable[R]): Iterable[R] =
    mapTopLevel((_, typeSpec) => typeSpec.mapRec(proc))

  /**
    * Calls certain function on all top-level [[ClassSpec]] elements stored in this
    * ClassSpecs.
    *
    * @param proc function to execute on every encountered type.
    * @tparam R mandates that function must return a list of this type.
    */
  def mapTopLevel[R](proc: (String, ClassSpec) => Iterable[R]): Iterable[R] = {
    flatMap { case (specName, typeSpec) =>
      //      try {
      proc(specName, typeSpec)
      //      } catch {
      // TODO: try to emit more specific error, with a reference to current file
      //      }
    }
  }

  def importRelative(name: String, path: List[String], inFile: Option[String]): Future[Option[ClassSpec]]
  def importAbsolute(name: String, path: List[String], inFile: Option[String]): Future[Option[ClassSpec]]
}
