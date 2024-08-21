def teamstemplate = '''
{
    "@type": "MessageCard",
    "@context": "http://schema.org/extensions",
    "summary": "This is an alert message",
    "themeColor": "0076D7",
    "sections": [{
        "activityTitle": "This is an alert message",
        "activitySubtitle": "On the production instance",
        "activityImage": "https://scontent-arn2-1.cdninstagram.com/v/t51.2885-19/85065400_2560714410851294_5774335430686146560_n.jpg?stp=dst-jpg_s150x150&_nc_ht=scontent-arn2-1.cdninstagram.com&_nc_cat=102&_nc_ohc=Pvi3SU4qGeQAX_KT9HR&edm=ABfd0MgBAAAA&ccb=7-4&oh=00_AT_y5AwLEP082mECMN6v_EtkLEPo1iGiMAPDoO_dS8m4MQ&oe=62229844&_nc_sid=7bff83",
        "facts": [
          {
            "name": "Cause",
            "value": "{{ctx.monitor.name}}."
          }
     ],
        "markdown": true
    }]
}
'''

def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
}
