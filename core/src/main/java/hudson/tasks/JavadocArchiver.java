package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.maven.AbstractMavenProject;
import hudson.model.*;
import hudson.util.FormFieldValidator;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * Saves Javadoc for the project and publish them. 
 *
 * @author Kohsuke Kawaguchi
 */
public class JavadocArchiver extends Publisher {
    /**
     * Path to the Javadoc directory in the workspace.
     */
    private final String javadocDir;
    /**
     * If true, retain javadoc for all the successful builds.
     */
    private final boolean keepAll;
    
    @DataBoundConstructor
    public JavadocArchiver(String javadoc_dir, boolean keep_all) {
        this.javadocDir = javadoc_dir;
        this.keepAll = keep_all;
    }

    public String getJavadocDir() {
        return javadocDir;
    }

    public boolean isKeepAll() {
        return keepAll;
    }

    /**
     * Gets the directory where the Javadoc is stored for the given project.
     */
    private static File getJavadocDir(AbstractItem project) {
        return new File(project.getRootDir(),"javadoc");
    }

    /**
     * Gets the directory where the Javadoc is stored for the given build.
     */
    private static File getJavadocDir(Run run) {
        return new File(run.getRootDir(),"javadoc");
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        listener.getLogger().println(Messages.JavadocArchiver_Publishing());

        FilePath javadoc = build.getParent().getWorkspace().child(javadocDir);
        FilePath target = new FilePath(keepAll ? getJavadocDir(build) : getJavadocDir(build.getProject()));

        try {
            if (javadoc.copyRecursiveTo("**/*",target)==0) {
                if(build.getResult().isBetterOrEqualTo(Result.UNSTABLE)) {
                    // If the build failed, don't complain that there was no javadoc.
                    // The build probably didn't even get to the point where it produces javadoc. 
                    listener.error(Messages.JavadocArchiver_NoMatchFound(javadoc));
                }
                build.setResult(Result.FAILURE);
                return true;
            }
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace(listener.fatalError(Messages.JavadocArchiver_UnableToCopy(javadoc,target)));
            build.setResult(Result.FAILURE);
             return true;
        }
        
        // add build action, if javadoc is recorded for each build
        if(keepAll)
            build.addAction(new JavadocBuildAction(build));
        
        return true;
    }

    public Action getProjectAction(Project project) {
        return new JavadocAction(project);
    }

    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<Publisher> DESCRIPTOR = new DescriptorImpl();

    protected static abstract class BaseJavadocAction implements Action {
        public String getUrlName() {
            return "javadoc";
        }

        public String getDisplayName() {
            if (new File(dir(), "help-doc.html").exists())
                return Messages.JavadocArchiver_DisplayName_Javadoc();
            else
                return Messages.JavadocArchiver_DisplayName_Generic();
        }

        public String getIconFileName() {
            if(dir().exists())
                return "help.gif";
            else
                // hide it since we don't have javadoc yet.
                return null;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
            new DirectoryBrowserSupport(this, getTitle())
                .serveFile(req, rsp, new FilePath(dir()), "help.gif", false);
        }

        protected abstract String getTitle();

        protected abstract File dir();
    }

    public static class JavadocAction extends BaseJavadocAction implements ProminentProjectAction {
        private final AbstractItem project;

        public JavadocAction(AbstractItem project) {
            this.project = project;
        }

        protected File dir() {
            // Would like to change AbstractItem to AbstractProject, but is
            // that a backwards compatible change?
            if (project instanceof AbstractProject) {
                AbstractProject abstractProject = (AbstractProject) project;

                Run run = abstractProject.getLastSuccessfulBuild();
                if (run != null) {
                    File javadocDir = getJavadocDir(run);

                    if (javadocDir.exists())
                        return javadocDir;
                }
            }

            return getJavadocDir(project);
        }

        protected String getTitle() {
            return project.getDisplayName()+" javadoc";
        }
    }
    
    public static class JavadocBuildAction extends BaseJavadocAction {
    	private final AbstractBuild<?,?> build;
    	
    	public JavadocBuildAction(AbstractBuild<?,?> build) {
    	    this.build = build;
    	}

        protected String getTitle() {
            return build.getDisplayName()+" javadoc";
        }

        protected File dir() {
            return getJavadocDir(build);
        }
    }

    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private DescriptorImpl() {
            super(JavadocArchiver.class);
        }

        public String getDisplayName() {
            return Messages.JavadocArchiver_DisplayName();
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.WorkspaceDirectory(req,rsp).process();
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // for Maven, javadoc archiving kicks in automatically
            return !AbstractMavenProject.class.isAssignableFrom(jobType);
        }
    }
}
