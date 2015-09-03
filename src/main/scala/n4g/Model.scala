package n4g

object Model {

  type Id = String
  type Timestamp = Int

  case class File(repo: String, // repo origin url
                  path: String,
                  lines: Int,
                  name: String,
                  ext: String, // file extension (.scala, .js, ...)
                  purpose: String) // src vs. test

  case class User(name: String,
                  email: String)

  case class Commit(id: Id,
                    time: Timestamp,
                    user: User,
                    msg: String,
                    files: List[File],
                    parents: List[Id])

  case class Change(id: Id,
                    time: Timestamp,
                    msg: String,
                    user: User,
                    file: File)
}





