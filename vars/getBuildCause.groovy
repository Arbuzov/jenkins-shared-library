def call() {
  return (currentBuild.rawBuild.getCauses().collect { it.toString().takeWhile { it != '@' } })
}