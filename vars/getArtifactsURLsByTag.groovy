import hudson.model.*
import com.jenkinsci.plugins.badge.action.BadgeAction
import org.korosoft.jenkins.plugin.rtp.BuildRichTextAction
import com.jenkinsci.plugins.badge.action.BadgeSummaryAction
import java.util.regex.Pattern

@NonCPS
def call(fullName, tagName) {
   reportUsage(getClass().protectionDomain.codeSource.location.path)
   def URLs = [ ]
   jenkins.model.Jenkins.instance.getItemByFullName(fullName).getBuilds().each{ build->
     build.getActions(com.jenkinsci.plugins.badge.action.BadgeAction).each{ action->
       if (action.getText().equals(tagName)) {
          build.getActions(org.korosoft.jenkins.plugin.rtp.BuildRichTextAction).each{ action2->
             def pattern = Pattern.compile("(?<=')http[^\']*")
             def matcher = action2.getRichText() =~ pattern
             URLs = [URLs, matcher.findAll()].flatten()
          }
          build.getActions(com.jenkinsci.plugins.badge.action.BadgeSummaryAction).each{ action2->
             def pattern = Pattern.compile("(?<=')http[^\']*")
             def matcher = action2.getRawText() =~ pattern
             URLs = [URLs, matcher.findAll()].flatten()
          }
       }
     }
   }
   return URLs
}