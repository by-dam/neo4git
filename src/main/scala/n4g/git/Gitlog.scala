package n4g.git

import java.nio.file.Paths
import _root_.n4g.Model.{Commit, File, User}
import n4g.Model
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE
import org.eclipse.jgit.diff.{DiffEntry, DiffFormatter, RawTextComparator}
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.{PersonIdent, Repository, ObjectId}
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.NullOutputStream

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.io.Source.fromFile
import scala.util.Try

object Gitlog {

  /**
   * Print the commit log to stdout
   * @param path path to a git repo
   */
  def log(path: String, days:Option[Int]=None): Iterator[Commit] = {
    implicit val repo = new FileRepository(path + java.io.File.separator + ".git")
    val git = new Git(repo)
    val log = git.log().call()
    log.asScala.filter(withinLast(days)).map(commit).iterator
  }

  private def filesInCommit(commit: RevCommit)(implicit repo: Repository): List[File] =
    if (commit.getParentCount() == 0) {
      filesInFirstCommit(commit)
    } else {
      val parent = commit.getParent(0)
      val df = new DiffFormatter(NullOutputStream.INSTANCE)
      df.setRepository(repo)
      df.setDiffComparator(RawTextComparator.DEFAULT)
      df.setDetectRenames(true)
      df.scan(parent.getTree(), commit.getTree()).asScala.map(file).toList
    }


  private def filesInFirstCommit(commit: RevCommit)(implicit repo: Repository): List[File] = {
    val url = repoUrl(repo)
    val tw = new TreeWalk(repo)
    tw.reset()
    tw.setRecursive(true)
    tw.addTree(commit.getTree())

    val buffer = ListBuffer[Model.File]()
    while (tw.next()) {
      val path = tw.getPathString
      buffer += File(url, path, lines(path), filename(path), ext(path), purpose(path))
    }
    tw.close
    buffer.toList
  }

  private def withinLast(days: Option[Int])(commit: RevCommit): Boolean = days match {
    case None => true
    case Some(d) => (commit.getCommitTime.toLong + d * 86400) * 1000 > System.currentTimeMillis
  }

  private def repoUrl(repo: Repository): String = repo.getConfig.getString("remote", "origin", "url")

  private def file(entry: DiffEntry)(implicit repo: Repository): File = {
    val path = if (entry.getChangeType == DELETE) entry.getOldPath
               else entry.getNewPath
    File(repoUrl(repo), path, lines(path), filename(path), ext(path), purpose(path))
  }

  private def filename(path: String): String = Try {
    Paths.get(path).getFileName.toString
  }.getOrElse("")

  private def ext(path: String) = Try{
    val index = path.lastIndexOf(".") + 1
    if (index > 0) path.substring(index)
    else ""
  }.getOrElse("")

  private def purpose(path: String): String = Try {
    val elems = Paths.get(path).iterator.asScala.map(_.getFileName.toString)
    if (elems.contains("test")) "test"
    else "src"
  }.getOrElse("src")

  private def lines(path: String)(implicit repo: Repository): Int = Try {
    val dir = repo.getDirectory.getPath
    val filePath = dir.substring(0, dir.length - 4) + path // remove .git folder from path
    fromFile(filePath).getLines.size
  }.getOrElse(0)

  private def user(person: PersonIdent): User = User(
    name = person.getName,
    email = person.getEmailAddress
  )

  private def commit(commit: RevCommit)(implicit repo: Repository): Commit = Commit(
    id = ObjectId.toString(commit.getId),
    time = commit.getCommitTime,
    user = user(commit.getAuthorIdent),
    msg = commit.getShortMessage,
    files = filesInCommit(commit),
    parents = commit.getParents.toList.map(c => ObjectId.toString(c.getId))
  )



}