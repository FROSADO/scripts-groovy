package scriptrunner

/**
 * Created by eferosa on 28/10/2015.
 */
import com.atlassian.crowd.embedded.api.CrowdService
import com.atlassian.crowd.embedded.api.Group
import com.atlassian.crowd.embedded.api.User
import com.atlassian.crowd.embedded.api.UserWithAttributes
import com.atlassian.crowd.embedded.impl.ImmutableUser
import com.atlassian.jira.bc.user.UserService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.security.groups.GroupManager
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.ApplicationUsers
import com.atlassian.jira.user.util.UserUtil
import org.apache.log4j.Level
import org.apache.log4j.Logger


Logger log = log
log.setLevel(Level.INFO)
String http = "https://"
String server = "web-server/jira"

int numOfDays = 365 // Number of days the user was not logged in
Date dateLimit = (new Date())- numOfDays
Date createdLimit = (new Date()) - 60

UserUtil userUtil = ComponentAccessor.userUtil
CrowdService crowdService = ComponentAccessor.crowdService
UserService userService = ComponentAccessor.getComponent(UserService.class)
GroupManager groupManager = ComponentAccessor.groupManager

User updateUser
UserService.UpdateUserValidationResult updateUserValidationResult

long count = 0
String msg = "<table>"
userUtil.getUsers().findAll{it.isActive()}.each {

    UserWithAttributes user = crowdService.getUserWithAttributes(it.getName())
    String lastLoginMillis = user.getValue('login.lastLoginMillis')
    Date created = new Date(it.createdDate.getTime());

    if (lastLoginMillis?.isNumber()) {
        // Users have not logged before limit time is a candidate to be deactivated
        Date lastLoginDate = new Date(Long.parseLong(lastLoginMillis))
        if (lastLoginDate.before(dateLimit)) {

            if (created.before(createdLimit)) {
                SortedSet<Group> groups = userUtil.getGroupsForUser(user.name)
                String grp = "";
                groups.each {
                    grp += "$it.name <br />";
                }
                msg += "<td>User <a href='${http}${server}/secure/ViewProfile.jspa?name=${user.name}'>${user.name} : ${user.displayName}</a></td><td>candidate to be desactivated.</td><td> $it.createdDate</td><td>$lastLoginDate</td><td>$grp</td>";
                def outMsg = disableUser(user);
                msg += "<tr><td>&nbsp;</td><td colspan='4'>$outMsg</td></tr>"
                count++;
            }
        }
    } else {
        // Users never have logged and created before "created limit", no body have use it.
        if (created.before(createdLimit)) {
            SortedSet<Group> groups = userUtil.getGroupsForUser(user.name)
            String grp = "";
            groups.each {
                grp += "$it.name <br />";
            }
            msg += "<tr><td>User <a href='${http}${server}/secure/ViewProfile.jspa?name=${user.name}'>${user.name} : ${user.displayName}</a></td><td>candidate to be desactivated.</td><td> $it.createdDate</td><td>Never logged</td><td>$grp</td></tr>";
            def outMsg = disableUser(user);
            msg += "<tr><td>&nbsp;</td><td colspan='4'>$outMsg</td></tr>"
            count++;
        }

        count++;
    }
}

return "${count} users deactivated.\n" + msg + "</table>";

def disableUser(User user) {

    UserUtil userUtil = ComponentAccessor.userUtil
    UserService userService = ComponentAccessor.getComponent(UserService.class)
    updateUser = ImmutableUser.newUser(user).active(false).emailAddress("Disabled-Accounts@company.name").toUser()
    ApplicationUser appUser = ApplicationUsers.from(updateUser)
    updateUserValidationResult = userService.validateUpdateUser(appUser)
    String msg = "";
    if (updateUserValidationResult.isValid()) {
        userService.updateUser(updateUserValidationResult)
        msg +=   "Deactivated ${updateUser.name} <br />"
        SortedSet<Group> groups = userUtil.getGroupsForUser(user.name)
        groups.each { group ->
            userUtil.removeUserFromGroup(group,user)
            msg += "Removed from group $group.name <br>"
        }
    } else {
        msg += "Update of ${user.name} failed: ${updateUserValidationResult.getErrorCollection().getErrors().entrySet().join(',')} <br/>"
    }
    return msg;

}