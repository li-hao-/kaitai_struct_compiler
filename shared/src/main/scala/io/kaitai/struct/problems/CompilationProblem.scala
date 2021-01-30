package io.kaitai.struct.problems

import fastparse.StringReprOps
import io.kaitai.struct.{JSON, Jsonable, Utils}
import io.kaitai.struct.datatype.DataType
import io.kaitai.struct.exprlang.Expressions
import io.kaitai.struct.format.{Identifier, KSVersion}

/**
  * Abstract top-level trait common to all problems which might be raised during
  * precompilation / compilation process in a Kaitai Struct compiler.
  */
sealed abstract class CompilationProblem extends Jsonable {
  def severity: ProblemSeverity
  def coords: ProblemCoords
  def text: String
  def message = s"${coords.message}: ${severity.message}: $text"
  def localizedInFile(fileName: String): CompilationProblem

  def toException = CompilationProblemException(this)

  override def toJson: String =
    JSON.mapToJson(
      (coords.toSeq ++ Seq("message" -> text)).toMap
    )
}

/**
  * Unified YAML parser exception which pinpoints the row/col of the problem.
  * Used to re-wrap YAML parser library-specific exceptions into this unified
  * format that neutral codebase understands.
  * @param text user-readable error message
  */
case class YAMLParserError(
  val text: String,
  val coords: ProblemCoords
) extends CompilationProblem {
  override def severity = ProblemSeverity.Error
  override def localizedInFile(fileName: String): CompilationProblem =
    copy(coords = coords.copy(file = Some(fileName)))
}

case class KSYParseError(
  val text: String,
  path: List[String],
  fileName: Option[String] = None
) extends CompilationProblem {
  override def severity: ProblemSeverity = ProblemSeverity.Error
  override val coords: ProblemCoords = ProblemCoords(fileName, Some(path))
  override def localizedInFile(fileName: String): CompilationProblem =
    copy(fileName = Some(fileName))
}

object KSYParseError {
  def withText(text: String, path: List[String]): CompilationProblemException =
    KSYParseError(text, path).toException

  def noKey(path: List[String]) =
    withText(s"missing mandatory argument `${path.last}`", path)

  def noKeys(path: List[String], expectedKeys: Set[String]) =
    withText(s"expected any of ${expectedKeys.toList.sorted.mkString(", ")}, found none", path)

  def badType(expected: String, got: Any, path: List[String]) = {
    val gotStr = got match {
      case null => "null"
      case _ => s"$got (${got.getClass})"
    }
    withText(s"expected $expected, got $gotStr", path)
  }

  def badDictValue(expected: Set[String], got: String, path: List[String]) =
    withText(s"expected ${expected.toList.sorted.mkString(" / ")}, got '$got'", path)

  def incompatibleVersion(expected: KSVersion, got: KSVersion, path: List[String]) =
    withText(
      s"this ksy requires compiler version at least $expected, but you have $got",
      path
    )

  def invalidId(id: String, entity: String, path: List[String]) =
    withText(
      s"invalid $entity ID: '$id', expected /${Identifier.ReIdentifier.toString}/",
      path
    )

  def expression(epe: Expressions.ParseException, path: List[String]) = {
    val f = epe.failure
    val pos = StringReprOps.prettyIndex(f.extra.input, f.index)

    // Try to diagnose most common errors and provide a friendly suggestion
    val lookup2 = Utils.safeLookup(epe.src, f.index, 2)
    val suggestion: String = (if (lookup2 == "&&") {
      Some("and")
    } else if (lookup2 == "||") {
      Some("or")
    } else {
      None
    }).map((x) => s", did you mean '$x'?").getOrElse("")

    f.extra.traced.expected

    withText(
      s"parsing expression '${epe.src}' failed on $pos, " +
        s"expected ${f.extra.traced.expected.replaceAll("\n", "\\n")}$suggestion",
      path
    )
  }

  def exprType(expected: String, got: DataType, path: List[String]) =
    withText(s"invalid type: expected $expected, got $got", path)

  def badProcess(got: String, path: List[String]) =
    withText(s"incorrect process expression `$got`", path)

  def invalidParamCount(paramSize: Int, argSize: Int, path: List[String]) =
    withText(s"parameter count mismatch: $paramSize declared, but $argSize used", path)
}

/**
  * Container for a real exception that happened due to some known problem
  * with input file, and we know where exactly is the culprit (path and file).
  * @param err inner exception
  * @param path YAML path components in file
  * @param file file to report as erroneous, None means "main compilation unit"
  */
case class ErrorInInput(err: Throwable, path: List[String] = List(), fileName: Option[String] = None)
  extends CompilationProblem {

  override def text = Option(err.getMessage).getOrElse (err.toString)
  override val coords: ProblemCoords = ProblemCoords(fileName, Some(path))
  override def localizedInFile(fileName: String): CompilationProblem =
    copy(fileName = Some(fileName))
  override def severity: ProblemSeverity = ProblemSeverity.Error
}

case class ExpressionTypeError(expected: String, got: DataType, path: List[String], fileName: Option[String] = None)
  extends CompilationProblem {

  override def text = s"invalid type: expected $expected, got $got"
  override val coords: ProblemCoords = ProblemCoords(fileName, Some(path))
  override def localizedInFile(fileName: String): CompilationProblem =
    copy(fileName = Some(fileName))
  override def severity: ProblemSeverity = ProblemSeverity.Error
}

case class ParamMismatchError(idx: Int, argType: DataType, paramName: String, paramType: DataType, path: List[String], fileName: Option[String] = None)
  extends CompilationProblem {

  override def text = s"can't pass argument #$idx of type $argType into parameter `$paramName` of type $paramType"
  override val coords: ProblemCoords = ProblemCoords(fileName, Some(path))
  override def localizedInFile(fileName: String): CompilationProblem =
    copy(fileName = Some(fileName))
  override def severity: ProblemSeverity = ProblemSeverity.Error
}
