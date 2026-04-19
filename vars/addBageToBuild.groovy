import com.jenkinsci.plugins.badge.action.BadgeAction

@NonCPS
def call(build, badge, url = 'none') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def action = BadgeAction.createShortText(badge, 'white', 'green', 'white', 'white', url)
  build.addAction(action)
}

def call (Object args) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def build = args.build
  def badge = args.badge
  def url = args.url ?: 'none'
  return this.call(build, badge, url)
}