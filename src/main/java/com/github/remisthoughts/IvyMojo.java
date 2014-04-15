package com.github.remisthoughts;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultIncludeRule;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.MDArtifact;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.ExactOrRegexpPatternMatcher;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.impl.ArtifactResolver;
import org.sonatype.aether.impl.internal.DefaultRepositorySystem;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.ArtifactProperties;

/**
 * Goal which adds ivy artifacts as dependencies
 */
@Mojo(name = "ivy", defaultPhase = LifecyclePhase.INITIALIZE)
public class IvyMojo extends AbstractMojo
{
	private static final String IVY_GROUP_MARKER = "ivy.";
	private static final String IVY_CONFIGURATION_MARKER = ".";
	private static final String CONF = "default";

	private class IvyArtifactResolver implements ArtifactResolver {
		private final Ivy ivy;
		private final ArtifactResolver delegate;

		IvyArtifactResolver(Ivy ivy, ArtifactResolver delegate) {
			this.ivy = ivy;
			this.delegate = delegate;
		}

		public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request) throws ArtifactResolutionException {
			if (request.getArtifact().getGroupId().startsWith(IVY_GROUP_MARKER)) {
				return convert(request, retrieve(request).get(0));
			} else {
				return delegate.resolveArtifact(session, request);
			}
		}

		private List<Artifact> retrieve(ArtifactRequest request) throws ArtifactResolutionException {
			try {
				String groupId = request.getArtifact().getGroupId().substring(IVY_GROUP_MARKER.length());
				return new ArrayList<Artifact>(resolve(
						ivy,
						groupId.substring(groupId.indexOf(IVY_CONFIGURATION_MARKER) + 1),
						request.getArtifact().getArtifactId(),
						request.getArtifact().getVersion(),
						request.getArtifact().getClassifier(),
						request.getArtifact().getExtension(),
						false, // not transitive, as this is for a single request
						groupId.substring(0, groupId.indexOf(IVY_CONFIGURATION_MARKER)))); 
			} catch (MojoExecutionException e) {
				throw new ArtifactResolutionException(Collections.singletonList(new ArtifactResult(request)));
			}
		}

		private ArtifactResult convert(ArtifactRequest request, Artifact mvnArtifact) {
			Map<String, String> props = new TreeMap<String, String>();
			props.put(ArtifactProperties.LANGUAGE, "java");
			props.put(ArtifactProperties.TYPE, mvnArtifact.getType());
			props.put(ArtifactProperties.CONSTITUTES_BUILD_PATH, "true");
			props.put(ArtifactProperties.INCLUDES_DEPENDENCIES, "false");
			return new ArtifactResult(request).
					setArtifact(new org.sonatype.aether.util.artifact.DefaultArtifact(
							mvnArtifact.getGroupId(),
							mvnArtifact.getArtifactId(),
							mvnArtifact.getClassifier(),
							mvnArtifact.getType(),
							mvnArtifact.getVersion()).
							setFile(mvnArtifact.getFile()).
							setProperties(props));
		}

		public List<ArtifactResult> resolveArtifacts(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests) throws ArtifactResolutionException {
			List<ArtifactResult> ret = new ArrayList<ArtifactResult>(requests.size());
			Collection<ArtifactRequest> delegateRequests = new ArrayList<ArtifactRequest>(requests.size());
			for (ArtifactRequest request : requests) {
				if (request.getArtifact().getGroupId().startsWith(IVY_GROUP_MARKER)) {
					for (Artifact mvnArtifact : retrieve(request)) {
						ret.add(convert(request, mvnArtifact));
					}
				} else {
					delegateRequests.add(request);
				}
			}
			ret.addAll(delegate.resolveArtifacts(session, delegateRequests));
			return ret;
		}
	}

	/**
	 * the project
	 */
	@Parameter(required = true, defaultValue = "${project}")
	public MavenProject project;

	/**
	 * A file or a URL of the Ivy settings.
	 */
	@Parameter(property = "settings", required = true)
	public String settings;

	/**
	 * Should we get dependencies of the dependencies?
	 */
	@Parameter(property = "transitive", defaultValue = "false", required = false)
	public boolean transitive;

	/**
	 * Multiple Maven-style {@code <dependencies>}.
	 */
	@Parameter(required = true)
	public ArtifactItem[] dependencies;

	/**
	 * What scope to add it to
	 */
	@Parameter(property = "scope", defaultValue = "compile", required = false)
	public String scope;

	@Component
	public RepositorySystem repoSystem;

	@Component
	public ArtifactResolver artifactResolver;

	public void execute() throws MojoExecutionException
	{
		IvySettings settings = new IvySettings();
		try {
			File settingsFile = new File(this.settings);
			if (settingsFile.exists()) {
				settings.load(settingsFile);
			} else {
				// maybe a URL?
				settings.load(new URL(this.settings));
			}
		} catch (Exception e) {
			throw new MojoExecutionException("couldn't load ivy settings from '" + this.settings + "'", e);
		}
		Ivy ivy = Ivy.newInstance(settings);

		for (ArtifactItem item : dependencies) {
			// we need to resolve now to calculate the transitive 
			// dependencies (if required)
			Set<Artifact> allResolved = resolve(
				ivy, 
				item.getGroupId(), 
				item.getArtifactId(), 
				item.getVersion(), 
				item.getClassifier(), 
				item.getType(), 
				transitive,
				item.getBaseVersion());
			for (Artifact resolved : allResolved) {
				Dependency dep = new Dependency();
				dep.setGroupId(resolved.getGroupId());
				dep.setArtifactId(resolved.getArtifactId());
				dep.setScope(scope);
				dep.setClassifier(resolved.getClassifier());
				dep.setOptional(false);
				dep.setType(resolved.getType());
				dep.setVersion(resolved.getVersion());
				project.getModel().addDependency(dep);
			}
		}

		// force a re-calc of the dependencies so maven picks up our new ones
		project.setDependencyArtifacts(null);

		((DefaultRepositorySystem) repoSystem).setArtifactResolver(new IvyArtifactResolver(ivy, artifactResolver));
	}

	private Set<Artifact> resolve(
			Ivy ivy,
			String groupId,
			String artifactId,
			String version,
			String classifier,
			String type,
			boolean transitive,
			String configuration // or NULL for "*"
		) throws MojoExecutionException
	{
		DefaultModuleDescriptor md = DefaultModuleDescriptor.newDefaultInstance(ModuleRevisionId.newInstance("maven-ivy-plugin", "resolution", "1.0"));
		md.addConfiguration(new Configuration(CONF));

		// add our single dependency
		ModuleRevisionId module = ModuleRevisionId.newInstance(groupId, artifactId, version);
		DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(md, module, true, false, transitive);
		dd.addDependencyConfiguration(CONF, configuration == null ? "*" : configuration);
		dd.addIncludeRule("", new DefaultIncludeRule(
				new ArtifactId(module.getModuleId(), classifier == null ? ".*" : classifier, type, type),
				new ExactOrRegexpPatternMatcher(),
				Collections.emptyMap()));
		md.addDependency(dd);

		ResolveOptions options = new ResolveOptions();
		options.setConfs(new String[] { CONF });
		options.setLog("default");
		options.setUseCacheOnly(false);
		ResolveReport report = null;
		try {
			report = ivy.resolve(md, options);
		} catch (Exception e) {
			throw new MojoExecutionException("couldn't load ivy artifact", e);
		}

		if (report.getAllArtifactsReports() == null) {
			throw new MojoExecutionException("no ivy artifacts resolved for artifact");
		}

		// we'll put the artifacts in a Set so if we get duplicates (e.g. from different configurations)
		// we'll only process them once.
		Set<Artifact> artifacts = new HashSet<Artifact>(1);

		for (ArtifactDownloadReport artifactReport : report.getAllArtifactsReports()) {
			ModuleRevisionId id = ((MDArtifact) artifactReport.getArtifact()).getModuleRevisionId();
			if (artifactReport.getArtifactOrigin() != null
			&& artifactReport.getArtifactOrigin().getArtifact() != null
			&& artifactReport.getArtifactOrigin().getArtifact().getName() != null 
			&& artifactReport.getLocalFile() != null) {
				String thisClassifier = artifactReport.getArtifactOrigin().getArtifact().getName();
				String filename = artifactReport.getLocalFile().getName();
				String thisType = filename.substring(filename.lastIndexOf('.') + 1);

				// if there's more than one configuration possible we can't just pick one at
				// random (!) as it may be private.
				String thisConfiguration = "*";

				if(type != null && !thisType.equals(type)) {
					continue;
				}
				if(classifier != null && !classifier.matches(thisClassifier)) {
					continue;
				}

				DefaultArtifactHandler handler = new DefaultArtifactHandler();
				handler.setAddedToClasspath(true);
				DefaultArtifact artifact = new DefaultArtifact(
						IVY_GROUP_MARKER + thisConfiguration + IVY_CONFIGURATION_MARKER + id.getOrganisation(),
						id.getName(),
						VersionRange.createFromVersion(id.getRevision()),
						scope,
						thisType,
						thisClassifier,
						handler,
						false);
				artifact.setFile(artifactReport.getLocalFile());
				artifact.setResolved(true);
				artifacts.add(artifact);
			}
		}

		if (artifacts.isEmpty()) {
			throw new MojoExecutionException(String.format("No artifacts resolved"));
		} else if (!transitive && artifacts.size() > 1) {
			throw new MojoExecutionException(String.format("Multiple artifacts resolved - %s consider adding <classifier>", artifacts));
		}
		return artifacts;
	}
}
