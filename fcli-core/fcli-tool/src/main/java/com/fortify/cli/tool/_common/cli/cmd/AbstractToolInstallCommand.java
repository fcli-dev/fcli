/*******************************************************************************
 * Copyright 2021, 2023 Open Text.
 *
 * The only warranties for products and services of Open Text 
 * and its affiliates and licensors ("Open Text") are as may 
 * be set forth in the express warranty statements accompanying 
 * such products and services. Nothing herein should be construed 
 * as constituting an additional warranty. Open Text shall not be 
 * liable for technical or editorial errors or omissions contained 
 * herein. The information contained herein is subject to change 
 * without notice.
 *******************************************************************************/
package com.fortify.cli.tool._common.cli.cmd;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fortify.cli.common.cli.mixin.CommonOptionMixins;
import com.fortify.cli.common.http.proxy.helper.ProxyHelper;
import com.fortify.cli.common.output.cli.cmd.AbstractOutputCommand;
import com.fortify.cli.common.output.cli.cmd.IJsonNodeSupplier;
import com.fortify.cli.common.output.transform.IActionCommandResultSupplier;
import com.fortify.cli.common.rest.unirest.GenericUnirestFactory;
import com.fortify.cli.common.util.FcliDataHelper;
import com.fortify.cli.common.util.FileUtils;
import com.fortify.cli.common.util.StringUtils;
import com.fortify.cli.tool._common.helper.ToolHelper;
import com.fortify.cli.tool._common.helper.ToolVersionCombinedDescriptor;
import com.fortify.cli.tool._common.helper.ToolVersionDownloadDescriptor;
import com.fortify.cli.tool._common.helper.ToolVersionInstallDescriptor;

import kong.unirest.UnirestInstance;
import lombok.Getter;
import lombok.SneakyThrows;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

public abstract class AbstractToolInstallCommand extends AbstractOutputCommand implements IJsonNodeSupplier, IActionCommandResultSupplier {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractToolInstallCommand.class);
    private static final Set<PosixFilePermission> binPermissions = PosixFilePermissions.fromString("rwxr-xr-x");
    @Getter @Option(names={"-v", "--version"}, required = true, descriptionKey="fcli.tool.install.version", defaultValue = "default") 
    private String version;
    @Getter @Option(names={"-d", "--install-dir"}, required = false, descriptionKey="fcli.tool.install.install-dir") 
    private File installDir;
    @Mixin private CommonOptionMixins.RequireConfirmation requireConfirmation;
    @Getter @Option(names={"--on-digest-mismatch"}, required = false, descriptionKey="fcli.tool.install.on-digest-mismatch", defaultValue = "fail") 
    private DigestMismatchAction onDigestMismatch;
    
    private static enum DigestMismatchAction {
        fail, warn
    }
    
    @Override
    public final JsonNode getJsonNode() {
        String toolName = getToolName();
        ToolVersionDownloadDescriptor descriptor = ToolHelper.getToolDownloadDescriptor(toolName).getVersionOrDefault(version);
        return downloadAndInstall(toolName, descriptor);
    }
    
    @Override
    public String getActionCommandResult() {
        return "INSTALLED";
    }
    
    @Override
    public boolean isSingular() {
        return true;
    }
    
    private final JsonNode downloadAndInstall(String toolName, ToolVersionDownloadDescriptor downloadDescriptor) {
        try {
            Path installPath = getInstallPathOrDefault(downloadDescriptor);
            Path binPath = getBinPath(downloadDescriptor);
            ToolVersionInstallDescriptor installDescriptor = new ToolVersionInstallDescriptor(downloadDescriptor, installPath, binPath);
            emptyExistingInstallPath(installDescriptor.getInstallPath());
            File downloadedFile = download(downloadDescriptor);
            checkDigest(downloadDescriptor, downloadedFile);
            install(installDescriptor, downloadedFile);
            ToolVersionCombinedDescriptor combinedDescriptor = ToolHelper.saveToolVersionInstallDescriptor(toolName, installDescriptor);
            return new ObjectMapper().<ObjectNode>valueToTree(combinedDescriptor);            
        } catch ( IOException e ) {
            throw new RuntimeException("Error installing "+getToolName(), e);
        }
    }

    private final File download(ToolVersionDownloadDescriptor descriptor) throws IOException {
        File tempDownloadFile = File.createTempFile("fcli-tool-download", null);
        tempDownloadFile.deleteOnExit();
        download(descriptor.getDownloadUrl(), tempDownloadFile);
        return tempDownloadFile;
    }
    
    private final Void download(String downloadUrl, File destFile) {
        UnirestInstance unirest = GenericUnirestFactory.getUnirestInstance("tool",
                u->ProxyHelper.configureProxy(u, "tool", downloadUrl));
        unirest.get(downloadUrl).asFile(destFile.getAbsolutePath(), StandardCopyOption.REPLACE_EXISTING).getBody();
        return null;
    }
    
    protected void install(ToolVersionInstallDescriptor descriptor, File downloadedFile) throws IOException {
        Path installPath = descriptor.getInstallPath();
        Files.createDirectories(installPath);
        InstallType installType = getInstallType();
        switch (installType) {
        // TODO Clean this up
        case COPY: Files.copy(downloadedFile.toPath(), installPath.resolve(StringUtils.substringAfterLast(descriptor.getOriginalDownloadDescriptor().getDownloadUrl(), "/")), StandardCopyOption.REPLACE_EXISTING); break;
        case EXTRACT_ZIP: FileUtils.extractZip(downloadedFile, installPath); break;
        default: throw new RuntimeException("Unknown install type: "+installType.name());
        }
        downloadedFile.delete();
        postInstall(descriptor);
        updateBinPermissions(descriptor.getBinPath());
    }

    @SneakyThrows
    protected Path getInstallPathOrDefault(ToolVersionDownloadDescriptor descriptor) {
        if ( installDir == null ) {
            installDir = FcliDataHelper.getFortifyHomePath().resolve(String.format("tools/%s/%s", getToolName(), descriptor.getVersion())).toFile();
        }
        return installDir.getCanonicalFile().toPath();
    }
    
    protected Path getBinPath(ToolVersionDownloadDescriptor descriptor) {
        return getInstallPathOrDefault(descriptor).resolve("bin");
    }
    
    protected abstract String getToolName();
    protected abstract InstallType getInstallType();
    protected abstract void postInstall(ToolVersionInstallDescriptor installDescriptor) throws IOException;
    
    private final void emptyExistingInstallPath(Path installPath) throws IOException {
        if ( Files.exists(installPath) && Files.list(installPath).findFirst().isPresent() ) {
            requireConfirmation.checkConfirmed();
            FileUtils.deleteRecursive(installPath);
        }
    }
    
    private final void checkDigest(ToolVersionDownloadDescriptor descriptor, File downloadedFile) {
        String actualDigest = FileUtils.getFileDigest(downloadedFile, descriptor.getDigestAlgorithm());
        String expectedDigest = descriptor.getExpectedDigest();
        if ( !actualDigest.equals(expectedDigest) ) {
            String msg = "Digest mismatch"
                    +"\n Expected: "+expectedDigest
                    +"\n Actual:   "+actualDigest;
            switch(onDigestMismatch) {
            case fail: throw new IllegalStateException(msg);
            case warn: LOG.warn(msg);
            }
        }
    }
    
    private static final void updateBinPermissions(Path binPath) throws IOException {
        try (Stream<Path> walk = Files.walk(binPath)) {
            walk.forEach(AbstractToolInstallCommand::updateFilePermissions);
        }
    }
        
    @SneakyThrows
    private static final void updateFilePermissions(Path p) {
        try {
            Files.setPosixFilePermissions(p, binPermissions);
        } catch ( UnsupportedOperationException e ) {
            // Log warning?
        }
    }
    
    protected static enum InstallType {
        EXTRACT_ZIP, COPY
    }
}
