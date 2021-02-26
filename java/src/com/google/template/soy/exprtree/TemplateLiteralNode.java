/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.template.soy.exprtree;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.NamedTemplateType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateImportType;
import javax.annotation.Nullable;

/** Node representing a template literal. */
public final class TemplateLiteralNode extends AbstractParentExprNode {

  private static final NamedTemplateType VARREF_PLACEHOLDER =
      NamedTemplateType.create("__varref__");

  private TriState isStaticCall = TriState.UNSET;
  private String templateFqn;
  private SoyType type;

  // This goes away when template call FQN goes away.
  public static TemplateLiteralNode forGlobal(GlobalNode global) {
    TemplateLiteralNode node = new TemplateLiteralNode(global.getSourceLocation());
    node.addChild(global);
    node.type = NamedTemplateType.create(global.getName());
    return node;
  }

  public static TemplateLiteralNode forVarRef(VarRefNode varRef) {
    return forVarRef(varRef, varRef.getSourceLocation());
  }

  public static TemplateLiteralNode forVarRef(VarRefNode varRef, SourceLocation sourceLocation) {
    TemplateLiteralNode node = new TemplateLiteralNode(sourceLocation);
    node.addChild(varRef);
    if (varRef.hasType() && varRef.getType().getKind() == SoyType.Kind.TEMPLATE_TYPE) {
      node.resolveTemplateName();
    } else {
      node.type = VARREF_PLACEHOLDER;
    }
    return node;
  }

  private TemplateLiteralNode(SourceLocation sourceLocation) {
    super(sourceLocation);
  }

  private TemplateLiteralNode(TemplateLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.isStaticCall = orig.isStaticCall;
    this.templateFqn = orig.templateFqn;
    this.type = orig.type;
  }

  /**
   * Whether this node references a template by its fully qualified name. If true then the only
   * child of this node is a GlobalNode.
   */
  public boolean isGlobalName() {
    return getChild(0).getKind() == Kind.GLOBAL_NODE;
  }

  /** Returns whether this node is the root expression of a CallNode. */
  public boolean isStaticCall() {
    Preconditions.checkState(isStaticCall.isSet(), "May not call before setStaticCall()");
    return isStaticCall == TriState.ENABLED;
  }

  public void setStaticCall(boolean isStaticCall) {
    this.isStaticCall = TriState.from(isStaticCall);
  }

  public void resolveTemplateName() {
    checkState(!isResolved(), "Template identifier has already been resolved.");

    if (isGlobalName()) {
      GlobalNode existingGlobal = (GlobalNode) getChild(0);
      templateFqn = existingGlobal.getIdentifier().identifier();
      existingGlobal.resolve(
          NamedTemplateType.create(templateFqn), new NullNode(existingGlobal.getSourceLocation()));

      // Only set the type if it hasn't been upgraded already.
      if (getType() instanceof NamedTemplateType) {
        type = NamedTemplateType.create(templateFqn);
      }
    } else {
      SoyType type = getChild(0).getType();
      switch (type.getKind()) {
        case TEMPLATE_TYPE:
          templateFqn = ((TemplateImportType) type).getName();
          this.type = type;
          break;
        default:
          throw new IllegalStateException("type: " + type.getKind() + " / " + type);
      }
    }
  }

  public boolean isResolved() {
    return templateFqn != null;
  }

  /** Returns the resolved template name, or null if not resolved yet. */
  @Nullable
  public String getResolvedName() {
    return templateFqn;
  }

  public Identifier getIdentifier() {
    Preconditions.checkArgument(isGlobalName(), "Only global node literals have identifiers.");
    return ((GlobalNode) getChild(0)).getIdentifier();
  }

  @Override
  public SoyType getType() {
    return type;
  }

  public void setType(SoyType type) {
    this.type = Preconditions.checkNotNull(type);
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_LITERAL_NODE;
  }

  @Override
  public String toSourceString() {
    return getChild(0).toSourceString();
  }

  @Override
  public TemplateLiteralNode copy(CopyState copyState) {
    return new TemplateLiteralNode(this, copyState);
  }
}
