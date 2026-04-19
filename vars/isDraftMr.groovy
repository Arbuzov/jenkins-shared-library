def call() {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  if (!(env.CHANGE_ID?.trim())) {
    return false
  }
  def title = (env.CHANGE_TITLE ?: env.gitlabMergeRequestTitle ?: env.GITLAB_OA_MR_TITLE ?: '').trim()
  if (!title) {
    return false
  }
  def normalized = title.toLowerCase()
  return normalized.startsWith('draft:') || normalized.startsWith('wip:') || normalized.startsWith('draft ') || normalized.startsWith('wip ')
}