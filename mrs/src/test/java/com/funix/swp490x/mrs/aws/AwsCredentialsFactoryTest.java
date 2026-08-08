package com.funix.swp490x.mrs.aws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class AwsCredentialsFactoryTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void resolvesAwsCliToAFullPathOnWindows() {
        String aws = AwsCredentialsFactory.resolveAwsExecutable();
        assertThat(aws).endsWith("aws.exe");
        assertThat(aws).contains("AWSCLIV2");
    }
}
