package com.intellij.flex.uiDesigner.mxml;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlText;

class XmlTextValueProvider implements XmlElementValueProvider {
  private XmlText xmlText;

  public void setXmlText(XmlText xmlText) {
    this.xmlText = xmlText;
  }

  @Override
  public String getTrimmed() {
    return xmlText.getText();
  }

  @Override
  public CharSequence getSubstituted() {
    return xmlText.getValue();
  }

  @Override
  public XmlElement getInjectedHost() {
    // only tag or attribute may be queried
    throw new UnsupportedOperationException();
  }
}
