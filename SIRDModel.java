

/**
 * A simple SIR-like epidemic model for diseases like COVID-19.
 * Includes consideration of incubation times (which makes is SEIR-model like) and calculation of deaths
 *
 * @author Peter Richterich 2020
 * 
 * @version 0.01 March 24, 2020
 * 
 * No warranties of any kind. 
 */
public class SIRDModel {
	
	private final int numModelDays = 30;	// the number of days we consider for each infection
	
	private double[] dayCount = new double[numModelDays];
	
	private int daysElapsed;
	
	// rate of death for each day after the infection
	// reports indicate death occurs 18 days after symptom onset, so ~ 23 days after infection
	// putting the day(s) of death earlier reduces the estimated number of infected for a given number of deaths
	// with limited medical care (or without), death will occur earlier
	private double[] deathRate = {	
			0, 0, 0, 0, 0, 
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0.006,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0
	};
	
	private double[] infectionRate = {	// relative infectiousness; COVID-19 is maximally infective on day 6
			0, 0.0, 0.1, 0.3, 0.8, 
			1, 0.8, 0.5, 0.2, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0
	};
	
	private double[] recoveryRate = {	// for simplicity, everyone alive recovers on day 21; only affects recovered counts
			0, 0, 0, 0, 0, 
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			0, 0, 0, 0, 0,
			1.0, 0, 0, 0, 0,
			0, 0, 0, 0, 0
	};
	
	// Effect if non-pharmaceutical interventions like travel restrictions, quarantine, social distancing, etc.
	private int[] npiDays;			// days interventions took effect, in descending order
	private double[] npiFactors;	// corresponding effect of interventions (1.0 = no effect, 0.0 = 100% reduction in transmissions)
	
	
	

	private double population, susceptible, recovered, dead;
	
	private Infected infected;	// disease parameters are defined in the Infected class
	
	
	/**
	 * Main method runs a model prints results to stdout
	 */
	public static void main(String[] args) {
		SIRDModel model;
		int population = 350000000; 	// about 350 million in the USA
		int initialInfected = 10; 	// number of infected at the start of the simulation
		int numDays = 210;

		model = new SIRDModel(population, initialInfected);
		model.run(numDays);
	}

	
	/**
	 * Constructor
	 * @param population Size of the population (if everyone is susceptible, as is assumed to be the case for COVID-19)
	 * @param initialInfected	Number of infections at day 0 in the model run.
	 */
	public SIRDModel(int population, int initialInfected) {
		this.population = population;
		infected = new Infected(population, initialInfected);
		susceptible = population - infected.size();
		recovered = 0;
		dead = 0;
		
		String modelString = "US50pct2step"; // "US50pct2step", "none", "80pct", or "80pct_relaxed100"

		if (modelString.equals("US50pct2step"))	{
			//  social distancing increasing in 2 steps, remaining constant thereafter
			npiFactors = new double[] {0.5, 0.75};	
			npiDays = new int[] {55,  51};	
			
		} else if (modelString.equals("none"))	{
			// no interventions
			npiDays = null;	
			npiFactors = null;	
			
		} else if (modelString.equals("80pct"))	{
			// social distancing works 80% after day 55
			npiDays = new int[] {51};	
			npiFactors = new double[] {0.2};	
			
		} else if (modelString.equals("80pct_relaxed100"))	{
			// social distancing works 80% after day 55, reduced to 25% after day 100
			npiDays = new int[] {100, 51};	
			npiFactors = new double[] {0.5, 0.2};	
		} else {
			throw new RuntimeException("No valid model defined");
		}
	}
	
	
	/**
	 * Run the model for the given number of days
	 */
	public void run (int days) {
		double totalInfections = infected.size();
		double newInfections = 0;
		double newDeath = 0;
		double newRecoveries = 0;

		// write header
		report(-1, susceptible, newInfections, totalInfections, infected.size, newRecoveries, recovered, newDeath, dead);
		int day = 0;
		// write starting conditions
		report(day, susceptible, newInfections, totalInfections, infected.size, newRecoveries, recovered, newDeath, dead);

		susceptible = population - infected.size() - recovered - dead;
		
		for (day = 1; day <= days; day++) {
			infected.nextDay(susceptible);
			newDeath = infected.getNewDeath();
			dead += newDeath;
			newInfections = infected.getNewInfections();
			totalInfections += newInfections;
			newRecoveries = infected.getNewRecovered();
			recovered += newRecoveries;
			susceptible = population - infected.size() - recovered - dead;
			// write today's results
			report(day, susceptible, newInfections, totalInfections, infected.size, newRecoveries, recovered, newDeath, dead);
		}
	}
		
	
	private void report(int day, double susceptible, double newInfections, double totalInfections, double activeInfections,
			double newRecoveries, double totalRecovered, double newDeath, double totalDeath) {
		if (day < 0) {
			System.out.println("day\tsusceptibly\tNew infections\tTotal infections\tActive\tNew recovered\tTotal recovered\tNew deaths\tTotal deaths");
			
		} else {
			System.out.printf ("%d\t%,d\t%,d\t%,d\t%,d\t%,d\t%d\t%,d\t%,d\n"
				, day, Math.round(susceptible), Math.round(newInfections), Math.round(totalInfections)
				, Math.round(activeInfections), Math.round(newRecoveries), Math.round(totalRecovered), Math.round(newDeath), Math.round(totalDeath)
				);
		}
	}


	/**
	 * Inner class that defines most of the disease-specific parameters,
	 * and tracks the infected for up to 30 (numModelDays) days so that observed distributions
	 * for infectivity, death, and recovery can be applied. 
	 * Of these, infectivity is the most important for how fast the disease spreads,
	 * while the death rate distribution determines the delays
	 *
	 */
	class Infected {

		private int populationSize;
		
		
		private double newDeath, newInfections, newRecovered;
		
		private double size = 0;
		
		
		public Infected( int population, int initialSize) {
			populationSize = population;
			size = initialSize;
			dayCount[0] = size;
		}
		
		public int size() {
			return (int) Math.round(size);
		}

		/**
		 * Calculate the number of new deaths
		 * @return
		 */
		public double newDeath() {
			// TODO Auto-generated method stub
			return 0;
		}

		public double getNewDeath() {
			return newDeath;
		}

		public double getNewInfections() {
			return newInfections;
		}
		
		public double getNewRecovered() {
			return newRecovered;
		}
		
		
		/** 
		 * Advance all numbers to the next day, which
		 * also calculates the number of new infections, recoveries, and deaths
		 */
		public void nextDay(double susceptible) {
			daysElapsed++;
			
			newDeath = 0;
			newInfections = 0;
			newRecovered = 0;
			
			double infectionSuccess = (susceptible / populationSize) * npiEffect(daysElapsed);
			
			for (int d = 0; d < numModelDays; d++) {
				// death
				double subdeath = dayCount[d] * deathRate[d];
				// new infections
				double subinfections = dayCount[d]* infectionRate[d] * infectionSuccess; 
				// recoveries
				double subrecovered = dayCount[d] * recoveryRate[d];
				
				dayCount[d] -= subdeath + subrecovered;
				
				newDeath += subdeath;
				newInfections += subinfections;
				newRecovered += subrecovered;
			}
			// move forward one day
			for (int i = numModelDays-1; i > 0; i--) {
				dayCount[i] = dayCount[i-1];
			}
			dayCount[0] = newInfections;
			
			// adjust and/or recompute size
			size += newInfections - newDeath - newRecovered;
		}

		
		/**
		 * Return the effect of non-pharmaceutical interventions for the given day
		 * (1.0 = no interventions or effect, 0.0 = 100% effective interventions, no transmissions)
		 */
		private double npiEffect(int daySinceFirstInfection) {
			if (npiDays != null) {
				for (int i = 0; i < npiDays.length; i++) {
					if (daySinceFirstInfection >= npiDays[i]) {
						return npiFactors[i];
					}
				}
			}

			return 1.0;
		}
	}
}
