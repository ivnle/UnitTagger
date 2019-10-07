package parser.coOccurMethods;

import catalog.Unit;
import iitb.shared.StringMap;

import java.util.List;

public interface Co_occurrenceScores {

	public abstract float[] getCo_occurScores(List<String> hdrToks,
			StringMap<Unit> units);

	public abstract boolean adjustFrequency();

	public abstract float freqAdjustedScore(float freq, float f);

}