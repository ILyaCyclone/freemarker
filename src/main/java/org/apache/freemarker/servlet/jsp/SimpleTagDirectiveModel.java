/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.freemarker.servlet.jsp;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.SimpleTag;
import javax.servlet.jsp.tagext.Tag;

import org.apache.freemarker.core.Environment;
import org.apache.freemarker.core.TemplateException;
import org.apache.freemarker.core.model.TemplateDirectiveBody;
import org.apache.freemarker.core.model.TemplateDirectiveModel;
import org.apache.freemarker.core.model.TemplateModel;

/**
 * Adapts a {@link SimpleTag}-based custom JSP tag to be a value that's callable in templates as an user-defined
 * directive. For {@link Tag}-based custom JSP tags {@link TagTransformModel} is used instead.
 */
class SimpleTagDirectiveModel extends JspTagModelBase implements TemplateDirectiveModel {
    protected SimpleTagDirectiveModel(String tagName, Class tagClass) throws IntrospectionException {
        super(tagName, tagClass);
        if (!SimpleTag.class.isAssignableFrom(tagClass)) {
            throw new IllegalArgumentException(tagClass.getName() + 
                    " does not implement either the " + Tag.class.getName() + 
                    " interface or the " + SimpleTag.class.getName() + 
                    " interface.");
        }
    }

    @Override
    public void execute(Environment env, Map args, TemplateModel[] outArgs,
                        final TemplateDirectiveBody body)
    throws TemplateException, IOException {
        try {
            SimpleTag tag = (SimpleTag) getTagInstance();
            final FreeMarkerPageContext pageContext = PageContextFactory.getCurrentPageContext();
            pageContext.pushWriter(new JspWriterAdapter(env.getOut()));
            try {
                tag.setJspContext(pageContext);
                JspTag parentTag = (JspTag) pageContext.peekTopTag(JspTag.class);
                if (parentTag != null) {
                    tag.setParent(parentTag);
                }
                setupTag(tag, args, pageContext.getObjectWrapper());
                if (body != null) {
                    tag.setJspBody(new JspFragment() {
                        @Override
                        public JspContext getJspContext() {
                            return pageContext;
                        }
                        
                        @Override
                        public void invoke(Writer out) throws JspException, IOException {
                            try {
                                body.render(out == null ? pageContext.getOut() : out);
                            } catch (TemplateException e) {
                                throw new TemplateExceptionWrapperJspException(e);
                            }
                        }
                    });
                    pageContext.pushTopTag(tag);
                    try {
                        tag.doTag();
                    } finally {
                        pageContext.popTopTag();
                    }
                } else {
                    tag.doTag();
                }
            } finally {
                pageContext.popWriter();
            }
        } catch (Exception e) {
            throw toTemplateModelExceptionOrRethrow(e);
        }
    }
    
    static final class TemplateExceptionWrapperJspException extends JspException {

        public TemplateExceptionWrapperJspException(Throwable cause) {
            super("Nested content has thrown template exception", cause);
        }
        
    }
    
}
