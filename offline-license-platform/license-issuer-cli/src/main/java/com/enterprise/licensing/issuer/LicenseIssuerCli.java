package com.enterprise.licensing.issuer;

import com.enterprise.licensing.issuer.commands.ActivationCommands;
import com.enterprise.licensing.issuer.commands.LicenseCommands;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "license-issuer", mixinStandardHelpOptions = true,
        description = "Offline vendor license issuance utility",
        subcommands = {LicenseCommands.Keygen.class, LicenseCommands.Issue.class,
                LicenseCommands.Inspect.class, LicenseCommands.Verify.class,
                ActivationCommands.InspectRequest.class, ActivationCommands.Activate.class,
                ActivationCommands.InspectCertificate.class})
public final class LicenseIssuerCli implements Runnable {
    public static int execute(String... args) {
        return new CommandLine(new LicenseIssuerCli()).execute(args);
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
