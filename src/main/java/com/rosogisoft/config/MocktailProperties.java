package com.rosogisoft.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mocktail")
public class MocktailProperties {

    private Deployment deployment = new Deployment();
    private Auth auth = new Auth();
    private Standalone standalone = new Standalone();
    private Mcp mcp = new Mcp();

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Standalone getStandalone() {
        return standalone;
    }

    public void setStandalone(Standalone standalone) {
        this.standalone = standalone;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public void setMcp(Mcp mcp) {
        this.mcp = mcp;
    }

    public DeploymentMode mode() {
        return deployment.mode;
    }

    public static class Deployment {
        private DeploymentMode mode = DeploymentMode.LDAP;

        public DeploymentMode getMode() {
            return mode;
        }

        public void setMode(DeploymentMode mode) {
            this.mode = mode;
        }
    }

    public static class Auth {
        private Ldap ldap = new Ldap();
        private Database database = new Database();

        public Ldap getLdap() {
            return ldap;
        }

        public void setLdap(Ldap ldap) {
            this.ldap = ldap;
        }

        public Database getDatabase() {
            return database;
        }

        public void setDatabase(Database database) {
            this.database = database;
        }
    }

    public static class Ldap {
        private String url = "ldap://localhost:389";
        private String baseDn = "dc=mockserver,dc=com";
        private String userSearchBase = "";
        private String userSearchFilter = "(sAMAccountName={0})";
        private String groupSearchBase = "ou=groups";
        private String managerDn = "";
        private String managerPassword = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBaseDn() {
            return baseDn;
        }

        public void setBaseDn(String baseDn) {
            this.baseDn = baseDn;
        }

        public String getUserSearchBase() {
            return userSearchBase;
        }

        public void setUserSearchBase(String userSearchBase) {
            this.userSearchBase = userSearchBase;
        }

        public String getUserSearchFilter() {
            return userSearchFilter;
        }

        public void setUserSearchFilter(String userSearchFilter) {
            this.userSearchFilter = userSearchFilter;
        }

        public String getGroupSearchBase() {
            return groupSearchBase;
        }

        public void setGroupSearchBase(String groupSearchBase) {
            this.groupSearchBase = groupSearchBase;
        }

        public String getManagerDn() {
            return managerDn;
        }

        public void setManagerDn(String managerDn) {
            this.managerDn = managerDn;
        }

        public String getManagerPassword() {
            return managerPassword;
        }

        public void setManagerPassword(String managerPassword) {
            this.managerPassword = managerPassword;
        }
    }

    public static class Database {
        private BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

        public BootstrapAdmin getBootstrapAdmin() {
            return bootstrapAdmin;
        }

        public void setBootstrapAdmin(BootstrapAdmin bootstrapAdmin) {
            this.bootstrapAdmin = bootstrapAdmin;
        }
    }

    public static class BootstrapAdmin {
        private String login = "admin";
        private String password = "";

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Standalone {
        private int userPort = 9000;

        public int getUserPort() {
            return userPort;
        }

        public void setUserPort(int userPort) {
            this.userPort = userPort;
        }
    }

    public static class Mcp {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
