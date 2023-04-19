String call(String delimeter = '/') {
  return (new Date()).format("yyyy${delimeter}MM${delimeter}dd")
}