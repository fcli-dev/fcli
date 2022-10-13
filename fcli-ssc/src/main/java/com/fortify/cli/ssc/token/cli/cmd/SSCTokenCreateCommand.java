/*******************************************************************************
 * (c) Copyright 2021 Micro Focus or one of its affiliates
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the 
 * "Software"), to deal in the Software without restriction, including without 
 * limitation the rights to use, copy, modify, merge, publish, distribute, 
 * sublicense, and/or sell copies of the Software, and to permit persons to 
 * whom the Software is furnished to do so, subject to the following 
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be included 
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY 
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE 
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
 * IN THE SOFTWARE.
 ******************************************************************************/
package com.fortify.cli.ssc.token.cli.cmd;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fortify.cli.common.output.cli.mixin.OutputConfig;
import com.fortify.cli.common.output.writer.output.StandardOutputWriterFactory;
import com.fortify.cli.common.rest.runner.config.IUrlConfig;
import com.fortify.cli.common.rest.runner.config.IUserCredentialsConfig;
import com.fortify.cli.common.util.DateTimePeriodHelper;
import com.fortify.cli.common.util.DateTimePeriodHelper.Period;
import com.fortify.cli.ssc.token.helper.SSCTokenConverter;
import com.fortify.cli.ssc.token.helper.SSCTokenCreateRequest;
import com.fortify.cli.ssc.token.helper.SSCTokenHelper;
import com.fortify.cli.ssc.util.SSCOutputConfigHelper;

import io.micronaut.core.annotation.ReflectiveAccess;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@ReflectiveAccess
@Command(name = "create")
public class SSCTokenCreateCommand extends AbstractSSCTokenCommand {
    private static final DateTimePeriodHelper PERIOD_HELPER = DateTimePeriodHelper.byRange(Period.MINUTES, Period.DAYS);
    @Mixin private StandardOutputWriterFactory outputWriterFactory;
    @Parameters(index="0", arity="1") private String type;
    @Option(names="--expire-in") private String expireIn;
    @Option(names="--description", defaultValue="Generated by 'fcli ssc token create' command", required=true) private String description;    
    
    @Override
    protected void run(SSCTokenHelper tokenHelper, IUrlConfig urlConfig, IUserCredentialsConfig userCredentialsConfig) {
        SSCTokenCreateRequest tokenCreateRequest = SSCTokenCreateRequest.builder()
                .type(type)
                .terminalDate(getExpiresAt())
                .description(description)
                .build();
        outputWriterFactory.createOutputWriter(getOutputConfig()).write(tokenHelper.createToken(urlConfig, userCredentialsConfig, tokenCreateRequest, JsonNode.class));
    }
    
    private OffsetDateTime getExpiresAt() {
        return expireIn==null ? null : PERIOD_HELPER.getCurrentOffsetDateTimePlusPeriod(expireIn);
    }
    
    private JsonNode transformRecord(JsonNode recordJsonNode) {
        ObjectNode record = (ObjectNode)recordJsonNode;
        String token = record.get("token").asText();
        record.put("restToken", token);
        record.put("applicationToken", SSCTokenConverter.toApplicationToken(token));
        record.remove("token");
        return record;
    }

    private OutputConfig getOutputConfig() {
        return SSCOutputConfigHelper.table()
                .recordTransformer(this::transformRecord);
    }
}
