/*
 * $Id$
 * -------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tools.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;

/**
 * Build a Mule plugin archive.
 *
 * @phase package
 * @goal mule-plugin
 * @requiresDependencyResolution runtime
 */
public class MulePluginMojo extends AbstractMuleMojo
{
    /**
     * @component
     */
    private MavenProjectHelper projectHelper;

    /**
     * Directory containing the classes.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    private File classesDirectory;

    /**
     * Whether a JAR file will be created for the classes in the app. Using this optional
     * configuration parameter will make the generated classes to be archived into a jar file
     * and the classes directory will then be excluded from the app.
     *
     * @parameter expression="${archiveClasses}" default-value="false"
     */
    private boolean archiveClasses;

    /**
     * List of exclusion elements (having groupId and artifactId children) to exclude from the
     * application archive.
     *
     * @parameter
     * @since 1.2
     */
    private List<Exclusion> exclusions;

    /**
     * List of inclusion elements (having groupId and artifactId children) to exclude from the
     * application archive.
     *
     * @parameter
     * @since 1.5
     */
    private List<Inclusion> inclusions;

    /**
     * Exclude all artifacts with Mule groupIds. Default is <code>true</code>.
     *
     * @parameter default-value="true"
     * @since 1.4
     */
    private boolean excludeMuleDependencies;

    /**
     * @parameter default-value="false"
     * @since 1.7
     */
    private boolean filterAppDirectory;

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        File plugin = getMuleZipFile();
        try
        {
            createMulePlugin(plugin);
        }
        catch (ArchiverException e)
        {
            throw new MojoExecutionException("Exception creating the Mule Plugin", e);
        }

        this.projectHelper.attachArtifact(this.project, "zip", plugin);
    }

    protected void createMulePlugin(final File plugin) throws MojoExecutionException, ArchiverException
    {
        MuleArchiver archiver = new MuleArchiver();
        addAppDirectory(archiver);
        addCompiledClasses(archiver);
        addDependencies(archiver);

        archiver.setDestFile(plugin);

        try
        {
            plugin.delete();
            archiver.createArchive();
        }
        catch (IOException e)
        {
            getLog().error("Cannot create archive", e);
        }
    }

    private void addAppDirectory(MuleArchiver archiver) throws ArchiverException
    {
        if (filterAppDirectory)
        {
            archiver.addResources(getFilteredAppDirectory());
        }
        else
        {
            archiver.addResources(appDirectory);
        }
    }

    private void addCompiledClasses(MuleArchiver archiver) throws ArchiverException, MojoExecutionException
    {
        if (!this.archiveClasses)
        {
            addClassesFolder(archiver);
        }
        else
        {
            addArchivedClasses(archiver);
        }
    }

    private void addClassesFolder(MuleArchiver archiver) throws ArchiverException
    {
        if (this.classesDirectory.exists())
        {
            getLog().info("Copying classes directly");
            archiver.addClasses(this.classesDirectory, null, null);
        }
        else
        {
            getLog().info(this.classesDirectory + " does not exist, skipping");
        }
    }

    private void addArchivedClasses(MuleArchiver archiver) throws ArchiverException, MojoExecutionException
    {
        if (!this.classesDirectory.exists())
        {
            getLog().info(this.classesDirectory + " does not exist, skipping");
            return;
        }

        getLog().info("Copying classes as a jar");

        final JarArchiver jarArchiver = new JarArchiver();
        jarArchiver.addDirectory(this.classesDirectory, null, null);
        final File jar = new File(this.outputDirectory, this.finalName + ".jar");
        jarArchiver.setDestFile(jar);
        try
        {
            jarArchiver.createArchive();
            archiver.addLib(jar);
        }
        catch (IOException e)
        {
            final String message = "Cannot create project jar";
            getLog().error(message, e);
            throw new MojoExecutionException(message, e);
        }
    }

    private void addDependencies(MuleArchiver archiver) throws ArchiverException
    {
        for (Artifact artifact : getArtifactsToArchive())
        {
            String message = String.format("Adding <%1s> as a lib", artifact.getId());
            getLog().info(message);

            archiver.addLib(artifact.getFile());
        }
    }

    private Set<Artifact> getArtifactsToArchive()
    {
        ArtifactFilter filter = new ArtifactFilter(this.project, this.inclusions,
            this.exclusions, this.excludeMuleDependencies);
        return filter.getArtifactsToArchive();
    }
}
