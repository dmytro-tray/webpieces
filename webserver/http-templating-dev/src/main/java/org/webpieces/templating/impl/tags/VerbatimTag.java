package org.webpieces.templating.impl.tags;

import org.webpieces.templating.api.AbstractTag;
import org.webpieces.templating.api.ScriptOutput;
import org.webpieces.templating.api.Token;

public class VerbatimTag extends AbstractTag {

	@Override
	public String getName() {
		return "verbatim";
	}
	
	@Override
	public void generateStart(ScriptOutput sourceCode, Token token) {
		sourceCode.println("      installNullFormatter();");
	}

	@Override
	public void generateEnd(ScriptOutput sourceCode, Token token) {
		sourceCode.println("      installHtmlFormatter();");
	}

}
