/*******************************************************************************
 * (c) Copyright 2020 Micro Focus or one of its affiliates
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
package com.fortify.cli.sc_dast.scan.cli.mixin;

import com.fortify.cli.common.variable.AbstractMinusVariableResolverMixin;
import com.fortify.cli.sc_dast.scan.cli.cmd.SCDastScanCommands;
import com.fortify.cli.sc_dast.scan.helper.SCDastScanDescriptor;
import com.fortify.cli.sc_dast.scan.helper.SCDastScanHelper;

import io.micronaut.core.annotation.ReflectiveAccess;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Spec.Target;

public class SCDastScanResolverMixin {
    @ReflectiveAccess
    public static abstract class AbstractSSCDastScanResolverMixin extends AbstractMinusVariableResolverMixin {
        @Getter private Class<?> MVDClass = SCDastScanCommands.class;
        public abstract String getScanId();

        public SCDastScanDescriptor getScanDescriptor(UnirestInstance unirest){
            return SCDastScanHelper.getScanDescriptor(unirest, resolveMinusVariable(getScanId()));
        }
        
        public String getScanId(UnirestInstance unirest) {
            return getScanDescriptor(unirest).getId();
        }
    }
    
    @ReflectiveAccess
    public static class RequiredOption extends AbstractSSCDastScanResolverMixin {
        @Getter @Setter(onMethod=@__({@Spec(Target.MIXEE)})) private CommandSpec mixee;
        @Option(names = {"--scan"}, required = true)
        @Getter private String scanId;
    }
    
    @ReflectiveAccess
    public static class PositionalParameter extends AbstractSSCDastScanResolverMixin {
        @Getter @Setter(onMethod=@__({@Spec(Target.MIXEE)})) private CommandSpec mixee;
        @Parameters(index = "0", arity = "1")
        @Getter private String scanId;
    }
}
