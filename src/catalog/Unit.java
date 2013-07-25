package catalog;

import gnu.trove.TFloatArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;


/**
 *
 * @author harsh
 * @author chander Changed package, access-specifiers and overrode toString() method.
 * 
 */
public class Unit {
	private String name;
	String baseNames[]; // separate out the synonyms, remove the paranthesis 
	Unit firstBaseNameParts[];
	private String symbol;
	String baseSyms[]; // separate out the alternative symbols, remove the paranthesis 
	private String conversionFactor;
	double multiplier;
	private ArrayList<String> lemmaSet;
	ArrayList<List<String> > lemmaTokens;
	private Quantity parentQuantity;
	private TFloatArrayList freqs;

	public Unit()
	{

	}

	public Unit(String Name,String Sym,String Conv,ArrayList<String> lemmas)
	{
		setName(Name);
		setSymbol(Sym);
		setConversionFactor(Conv);
		lemmaSet=lemmas;
	
	}
	/*
	 * need to parse all of these..
	 * 	kilocalorie; large calorie: 2 base names
	 *  fluid dram (US); US fluidram
	 *  gallon (US fluid; Wine): one base name.
	 *  
	 */
	private static String[] setBaseNames(String name) {
		if (name.contains("(") || name.contains(";")) {
			// remove content within paranthesis -- (there can be multiple) and split on ";"
			int index = name.indexOf('(');
			if (index > 0 && name.charAt(index-1)=='/') {
				index = name.indexOf('(',index+1);
			}
			for (; index >= 0; index = name.indexOf('(')) {
				int endIndex = name.indexOf(')', index+1);
				if (endIndex < 0) {
					break; // badly formed string.
				}
				name = name.substring(0,index) + " " + name.substring(endIndex+1);
			}
			name = name.trim();
			String names[] = name.split(";");
			for (int i = 0; i < names.length; i++) {
				names[i] = names[i].trim().toLowerCase();
			}
			return names;
		} else {
			return new String[]{name.trim().toLowerCase()};
		}
	}

	public Unit(Quantity quant) {
		parentQuantity = quant;
	}

	public String getName(){
		return name;
	}

	public String getSymbol(){
		return symbol;
	}

	public String getConversionFactor(){
		return conversionFactor;
	}

	public ArrayList<String> getLemmas(){
		return lemmaSet;
	}

	public void setName(String n){
		name=n;
		baseNames = setBaseNames(name);
	}
	public Unit getBaseNamePart(int p) {
		if (firstBaseNameParts != null && firstBaseNameParts.length > p)
			return firstBaseNameParts[p];
		if (p==0) return this;
		return null;
	}
	public void setSymbol(String symb){
		symbol=symb;
		baseSyms = setBaseNames(symbol);
	}

	public void setConversionFactor(String cf){
		// TODO -- would Decimal parse string work as well?
		conversionFactor=cf;
		String multStr = parseDecimalExpressionL(cf);
		//System.out.println("Parsed "+cf + " to "+multStr);
		if (multStr.length() > 0) {
			try {
			multiplier = Double.parseDouble(multStr);
			} catch (NumberFormatException e) {
				System.out.println(e.getMessage());
			}
		} else
			multiplier=1;
	}
	
	// parse strings of the form  "= 1.729 994 044×103"
	public static String parseDecimalExpressionL(String cf) throws NumberFormatException {
		// look for the first character that can be part of a digit.
		for (int c = 0; c < cf.length(); c++) {
			char ch = cf.charAt(c);
			if (Character.isDigit(ch) || ch=='.' || ch=='-' || ch=='+') {
				return parseDecimalExpression(cf.substring(c));
			}
		}
		return "";
	}
	public void  setLemmas(ArrayList<String> lemmas, TFloatArrayList freqs){
		lemmaSet=lemmas;
		this.freqs = freqs;
		if (lemmas != null && lemmas.size()>0) {
			lemmaTokens = new ArrayList<List<String>>();
			for (String lemma : lemmaSet) {
				lemmaTokens.add(QuantityCatalog.getTokens(lemma));
			}
		}
	}
	public Quantity getParentQuantity() {
		return parentQuantity;
	}
	public String toString()
	{
		return getBaseName();
		/*
		StringBuffer strBuff = new StringBuffer();
		strBuff.append("Name:="+name + " Symbol:="+symbol+" Conversion Factor:="+conversionFactor);
		if (lemmaSet!=null && lemmaSet.size() > 0) {
			strBuff.append(" Lemmas := ");
		for(String lemma : lemmaSet)
			strBuff.append(lemma).append(", ");
		}
		return strBuff.toString() ;
		*/
	}

	public String[] getBaseNames() {
		return baseNames;
	}
	public String[] getBaseSymbols() {
		return baseSyms;
	}
	public static void main(String args[]) throws Exception {
		Double.parseDouble("8.46e-3");
		String tests[][] = {{"kilocalorie; large calorie", "kilocalorie;large calorie"},
				{"fluid dram (US); US fluidram", "fluid dram;us fluidram"},
				{"gallon (US fluid; Wine)",  "gallon"},
				{"gallon (US fluid) (Wine)", "gallon"}
		};
		for (int i = 0; i < tests.length; i++) {
			String baseNames[] = Unit.setBaseNames(tests[i][0]);
			if (!Arrays.deepEquals(baseNames, tests[i][1].split(";")))
				throw new Exception("Code failure for " + tests[i][0]);
		}
		
		String ctests[][]={{"= 1.729 994 044×103", "1.729994044e3"}};
		for (int i = 0; i < ctests.length; i++) {
			String multi = Unit.parseDecimalExpressionL(ctests[i][0]);
			Double.parseDouble(multi);
			if (!ctests[i][1].equals(multi)) {
				throw new Exception("Error in parsing conversion string "+ctests[i][0] + " " + multi + " "+ctests[i][1]);
			}
		}
	}

	public double getMultiplier() {
		return multiplier;
	}
	public Unit baseUnit() {
		return this;
	}

	public String getBaseName() {
		return baseNames[0];
	}

	public float getLemmaFrequency(int l) {
		if (freqs==null || freqs.size() <= l) return 0;
		return freqs.get(l);
	}

	public boolean hasFrequency() {
		return freqs != null;
	}

	public List<String> getLemmaTokens(int l) {
		return lemmaTokens.get(l);
	}
	// parse strings of the form  "~ 1.729 994 044×103"
	public static String parseDecimalExpression(String doubleStr) {
		String vstr = "";
		// look for the first character that can be part of a digit.
		for (int c = 0; c < doubleStr.length(); c++) {
			char ch = doubleStr.charAt(c);
			if (Character.isDigit(ch) || ch=='.' || ch=='-' || ch=='+') {
				vstr = doubleStr.substring(c);
				break;
			}
		}
		if (vstr=="") return "";
		doubleStr = vstr;
		// look for the first character that may not be part of the mantissa.
		String remStr = "";
		if (doubleStr.length() > 0) {
			// remove the space until the first non-digit is seen.
			for (int c = 1; c < doubleStr.length(); c++) {
				char ch = doubleStr.charAt(c);
				if (!(Character.isDigit(ch) || ch=='.' || ch=='-' || ch=='+' || ch==' ' || ch=='e' || ch=='E' )) {
					remStr  = doubleStr.substring(c);
					doubleStr = doubleStr.substring(0,c);
					break;
				}
			}


			String parts[] = doubleStr.split(" ");
			doubleStr = "";
			for (int i = 0; i < parts.length; i++) {
				doubleStr += parts[i];
			}
			remStr = remStr.trim();

			if (remStr.startsWith("±")) {
				for (int c = 1; c < remStr.length(); c++) {
					char ch = remStr.charAt(c);
					if (!(Character.isDigit(ch) || ch=='.' || ch==' ' )) {
						remStr  = remStr.substring(c);
						break;
					}
				}
			}
			if (remStr.startsWith("×10") || remStr.startsWith("× 10")  || remStr.startsWith("x 10")) {
				int expStart = remStr.indexOf("10") + 2;
				int expEnd = expStart+1;
				for (; expEnd < remStr.length() && Character.isDigit(remStr.charAt(expEnd));expEnd++) {
				}
				if (Character.isDigit(remStr.charAt(expEnd-1))) {
					doubleStr += "e"+remStr.substring(expStart,expEnd).trim().replace('−', '-');
				}
			}
		}
		if (doubleStr.endsWith(".")) {
			doubleStr = doubleStr.substring(0,doubleStr.length()-1);
		}
		return doubleStr;
	}

	public void setCompoundUnitParts(Unit unit1, Unit unit2) {
		firstBaseNameParts = new Unit[]{unit1,unit2};
	}
}
