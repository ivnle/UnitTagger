package parser.cfgTrainer;

import parser.UnitFeatures;

import java.util.List;

public class TrainingInstance {
	String hdr;
	String trueUnits;
	public TrainingInstance(List<UnitFeatures> extractedUnits, int unitsMatchedIndex) {
	}

	public TrainingInstance(String hdr, String trueUnits) {
		this.hdr = hdr;
		this.trueUnits=trueUnits;
	}

}
