package model
import java.io.InputStream

case class FileToServe(source: InputStream, ext: String)
