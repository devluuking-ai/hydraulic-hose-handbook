package com.example.data

import com.example.model.CalloutType
import com.example.model.Chapter
import com.example.model.ChapterSection

object DefaultChapters {
    fun get(): List<Chapter> {
        return listOf(
            Chapter(
                id = "chapter_1_intro",
                order = 1,
                title = "Chapter 1: Introduction to Hydraulic Hoses",
                subtitle = "Principles of High-Pressure Fluid Power",
                readingTimeMinutes = 4,
                thumbnail = "img_hose_intro",
                summary = "Learn the fundamental anatomy of hydraulic hoses: inner tube, reinforcement layers, and outer cover, plus working pressure vs burst pressure design safety factors.",
                sections = listOf(
                    ChapterSection(
                        heading = "1.1 The Role of Hydraulic Hoses",
                        text = "A hydraulic hose is a high-pressure, flexible conduit designed to transmit pressurized fluid between pumps, control valves, actuators, cylinders, and motors. Unlike rigid steel piping, hydraulic hoses absorb mechanical vibrations, tolerate thermal expansion, and allow fluid power transmission across articulating machine parts such as excavator booms, steering linkages, and press cylinders."
                    ),
                    ChapterSection(
                        heading = "1.2 Anatomy & Tri-Layer Construction",
                        text = "Regardless of pressure classification, nearly all modern hydraulic hoses consist of three distinct, concentric layers engineered to work in harmony under dynamic stress:\n\n" +
                                "• Inner Tube: The fluid-carrying core, typically manufactured from synthetic elastomers such as Nitrile (NBR), Neoprene, or PTFE. It must demonstrate chemical compatibility with mineral oils, synthetic esters, water-glycol fluids, or biodegradable hydraulic oils.\n\n" +
                                "• Reinforcement Layer: The structural backbone that resists internal pressure. It consists of high-tensile carbon steel wire braided into interlocking mesh or wound in alternating helical spirals.\n\n" +
                                "• Outer Protective Cover: The protective shield made of synthetic rubber or modified polymers. Its primary duty is resisting mechanical abrasion, ozone breakdown, sunlight (UV), chemical exposure, and atmospheric weathering.",
                        image = "img_hose_intro",
                        caption = "Figure 1.1: Anatomical cutaway of a wire-braided high pressure hydraulic hose."
                    ),
                    ChapterSection(
                        heading = "1.3 Working Pressure vs Burst Pressure",
                        text = "Every hydraulic hose has a rated Maximum Working Pressure (MWP). Under SAE J517 and ISO 1307 standards, standard hydraulic assemblies require a minimum 4:1 design factor (burst pressure is at least four times the maximum allowable working pressure). For static test pressures, proof testing is typically conducted at 2 times the working pressure.",
                        calloutType = CalloutType.INFO,
                        calloutTitle = "Engineering Standard Note",
                        calloutText = "Never exceed the rated maximum working pressure. System pressure spikes (water hammer effect) caused by rapid valve actuation can spike peak pressures 150% to 200% above normal relief valve settings."
                    )
                )
            ),
            Chapter(
                id = "chapter_2_types",
                order = 2,
                title = "Chapter 2: Hydraulic Hose Types & Ratings",
                subtitle = "SAE Specifications, Wire Braid vs. Spiral",
                readingTimeMinutes = 5,
                thumbnail = "img_hose_types",
                summary = "Explore SAE 100R standards (100R1, 100R2, 100R12, 100R13/R15), compare braided vs spiral wire reinforcement, and master the STAMPED sizing methodology.",
                sections = listOf(
                    ChapterSection(
                        heading = "2.1 Common SAE 100R Standard Classifications",
                        text = "The Society of Automotive Engineers (SAE) defines universal industry standards under SAE J517. The most prevalent specifications include:\n\n" +
                                "• SAE 100R1: Single wire braid reinforcement. Used for medium-pressure petroleum and water-base fluid applications (typically 500 to 2,750 PSI depending on diameter).\n\n" +
                                "• SAE 100R2: Two high-tensile wire braids. The industry standard workhorse for high-pressure mobile and industrial machinery (typically 1,160 to 5,000 PSI).\n\n" +
                                "• SAE 100R12: Four-spiral wire reinforcement designed for heavy-duty, high-impulse cycles (typically 3,000 to 4,000 PSI).\n\n" +
                                "• SAE 100R13 & 100R15: Constant pressure rating hoses (e.g. 5,000 PSI or 6,000 PSI across all sizes), utilizing four or six alternating spiral wire wraps for severe cyclic fatigue."
                    ),
                    ChapterSection(
                        heading = "2.2 Wire Braid vs. Spiral Wire Reinforcement",
                        text = "Braided hoses interweave steel wire strands in an over-under pattern. They offer superior bend flexibility and tighter bend radiuses, making them ideal for compact tractor compartments and industrial equipment.\n\n" +
                                "Spiral hoses wrap steel wire layers in opposite directions separated by thin bonding rubber. Because the wires never cross over one another, there is zero friction point wear between wires under repetitive pressure impulses. Spiral hoses are mandatory for severe impulse cycles like rock drills, excavators, and stamping presses.",
                        image = "img_hose_types",
                        caption = "Figure 2.1: Comparison between Wire Braid (1WB/2WB) and Four-Spiral (4SP/4SH) construction."
                    ),
                    ChapterSection(
                        heading = "2.3 The STAMPED Selection Rule",
                        text = "When selecting or replacing a hydraulic hose, engineers use the STAMPED checklist:\n\n" +
                                "• S (Size): Inside diameter (ID) in dash sizes (-04 = 1/4\", -08 = 1/2\", -16 = 1\"). Proper flow velocity prevents cavitation and heat buildup.\n" +
                                "• T (Temperature): Fluid and ambient temperature extremes.\n" +
                                "• A (Application): Environmental vibration, flex cycle, routing space.\n" +
                                "• M (Media): Chemical compatibility with the hydraulic fluid.\n" +
                                "• P (Pressure): System working pressure and pressure surges.\n" +
                                "• E (Ends): Correct fittings, threads, and sealing types.\n" +
                                "• D (Delivery): Volume flow rate (GPM or L/min) and testing requirements.",
                        calloutType = CalloutType.TIP,
                        calloutTitle = "Pro Tip: Flow Velocity",
                        calloutText = "For pressure lines, keep fluid velocity between 10 to 25 ft/sec (3 to 7.5 m/s). For suction lines, keep velocity under 2 to 4 ft/sec to prevent pump cavitation."
                    )
                )
            ),
            Chapter(
                id = "chapter_3_fittings",
                order = 3,
                title = "Chapter 3: Fittings, Couplings & Thread Identification",
                subtitle = "JIC 37°, ORFS, NPT & Crimp Assembly",
                readingTimeMinutes = 5,
                thumbnail = "img_hose_fittings",
                summary = "Identify hydraulic coupling standards: JIC 37° flare, O-Ring Face Seal (ORFS), NPT pipe threads, and permanent crimped ferrule tolerances.",
                sections = listOf(
                    ChapterSection(
                        heading = "3.1 Major Hydraulic Thread Standards",
                        text = "Correct thread identification is paramount for leak-free, high-pressure operation. The three most common North American types are:\n\n" +
                                "1. JIC 37° Flare (SAE J514): Features parallel UN/UNF threads with a 37-degree cone seat. Sealing is achieved metal-to-metal between the flared cone and the female swivel seat.\n\n" +
                                "2. O-Ring Face Seal (ORFS - SAE J1453): Employs a flat machined face with a captive elastomeric O-ring in a precision groove. Widely considered the most leak-resistant fitting for high-vibration mobile equipment.\n\n" +
                                "3. NPT / NPTF Pipe Threads: Tapered threads (1° 47' angle). Sealing relies on thread deformation and mating flank contact. Requires hydraulic thread sealant; prone to leakage if over-torqued or repeatedly serviced."
                    ),
                    ChapterSection(
                        heading = "3.2 Anatomy of a Crimped Hose End",
                        text = "A hydraulic hose fitting assembly consists of two primary elements: the insert (stem) that slides into the inner tube, and the outer ferrule (collar). During crimping, hydraulic dies compress the ferrule radially inward, forcing teeth into the outer rubber and gripping the high-tensile reinforcement wires.",
                        image = "img_hose_fittings",
                        caption = "Figure 3.1: Detailed cutaway of JIC 37° flare, ORFS, and NPT fittings with crimped collars."
                    ),
                    ChapterSection(
                        heading = "3.3 Skive vs. No-Skive Crimping",
                        text = "• Skive Hoses: Require removing a precise length of the outer rubber cover (and occasionally the inner tube for interlock designs) with a wire skiving tool prior to crimping, allowing direct metal-to-metal bite onto the reinforcement.\n\n" +
                                "• No-Skive Hoses: Feature specially engineered ferrules with hardened serrated inner teeth that pierce through the outer cover to grip the reinforcement directly without prior peeling.",
                        calloutType = CalloutType.WARNING,
                        calloutTitle = "Crimp Diameter Precision",
                        calloutText = "Always verify the finished crimp diameter using a calibrated digital caliper against the manufacturer's crimp specification chart. An under-crimped fitting can blow off under pressure; an over-crimped fitting crushes the inner tube and damages wire reinforcement."
                    )
                )
            ),
            Chapter(
                id = "chapter_4_install",
                order = 4,
                title = "Chapter 4: Installation & Routing Best Practices",
                subtitle = "Bend Radius, Layline Alignment & Clamping",
                readingTimeMinutes = 5,
                thumbnail = "img_hose_install",
                summary = "Master correct hose routing techniques: avoiding twists using the printed layline, respecting minimum bend radiuses, and installing proper clamp supports.",
                sections = listOf(
                    ChapterSection(
                        heading = "4.1 The Golden Rules of Hose Routing",
                        text = "Premature hydraulic hose failures rarely stem from manufacturing flaws; over 75% of failures are caused by improper installation, external abrasion, or excessive mechanical bending.\n\n" +
                                "Key installation rules:\n" +
                                "• Respect Minimum Bend Radius: Never bend a hose sharper than its rated minimum bend radius. Bending tighter kinks the inner tube, restricts flow, and concentrates cyclic fatigue.\n\n" +
                                "• Account for Length Changes: Hydraulic hoses contract up to 4% in length and expand up to 2% in diameter when pressurized. Always provide sufficient slack.\n\n" +
                                "• Prevent Mechanical Twisting: A 7-degree twist in a high-pressure hose can reduce its service life by up to 70%; a 10-degree twist can slash service life by 90%!"
                    ),
                    ChapterSection(
                        heading = "4.2 Using the Layline as a Visual Guide",
                        text = "The printed continuous text running along the length of the hose (the layline) provides essential specifications: manufacturer, SAE standard, inner diameter, working pressure, and manufacturing date code.\n\n" +
                                "During installation, use the layline as your alignment gauge: the layline must run straight without spiraling like a corkscrew. Always use two wrenches when tightening swivel fittings—one to back up the hex stem and prevent twisting, the other to torque the swivel nut.",
                        image = "img_hose_install",
                        caption = "Figure 4.1: Engineering guide illustrating proper bend radius, layline alignment, and clamp spacing."
                    ),
                    ChapterSection(
                        heading = "4.3 Clamping and Abrasion Protection",
                        text = "Secure long runs of hydraulic hose with cushioned split clamps spaced approximately every 18 inches (450 mm). For areas subject to contact with steel frames or adjacent moving hoses, install spiral plastic wraps, heat-shrink ballistic nylon chafe sleeves, or fiberglass fire sleeves.",
                        calloutType = CalloutType.TIP,
                        calloutTitle = "Dual Wrench Technique",
                        calloutText = "Never tighten a swivel fitting with a single wrench. Always hold the hose end hex motionless with a backup wrench while turning the swivel nut with a torque wrench to ensure zero torque transfers into the hose carcass."
                    )
                )
            ),
            Chapter(
                id = "chapter_5_safety",
                order = 5,
                title = "Chapter 5: Maintenance, Inspection & Safety Protocols",
                subtitle = "Fluid Injection Hazards, Cardboard Test & Inspections",
                readingTimeMinutes = 6,
                thumbnail = "img_hose_safety",
                summary = "Critical safety standards for hydraulic technicians: high-pressure fluid injection injury prevention, the cardboard leak test, and daily inspection checkpoints.",
                sections = listOf(
                    ChapterSection(
                        heading = "5.1 DANGER: High-Pressure Fluid Injection",
                        text = "A pinhole leak in a hydraulic hose under 3,000 PSI acts like a hypodermic needle. Hydraulic fluid escaping at high velocity can effortlessly penetrate work gloves, heavy clothing, and human skin.\n\n" +
                                "Fluid injection injuries may initially feel like a harmless insect sting, but injected petroleum-based oil causes rapid chemical necrosis, compartment syndrome, and severe tissue infection. Without immediate emergency surgical debridement, fluid injection frequently leads to surgical amputation or permanent disability.",
                        calloutType = CalloutType.WARNING,
                        calloutTitle = "CRITICAL SAFETY WARNING",
                        calloutText = "NEVER use bare or gloved hands to feel for hydraulic leaks or check pressure lines. ALWAYS de-energize system pressure, lock out controls, and inspect with cardboard or stiff paper!"
                    ),
                    ChapterSection(
                        heading = "5.2 The Safe Cardboard Inspection Method",
                        text = "When troubleshooting suspected pinhole leaks, technicians must:\n" +
                                "1. Wear safety glasses, face shield, and protective equipment.\n" +
                                "2. Hold a clean sheet of cardboard or stiff card stock attached to a wooden handle or rod.\n" +
                                "3. Move the cardboard along the pressurized hose line at a safe distance.\n" +
                                "4. Look for oil discoloration or puncture marks on the cardboard.",
                        image = "img_hose_safety",
                        caption = "Figure 5.1: High-pressure fluid injection hazard and safe cardboard leak detection technique."
                    ),
                    ChapterSection(
                        heading = "5.3 Visual Inspection Checklist & Retirement Criteria",
                        text = "Inspect all hydraulic lines during routine pre-shift walkarounds. Immediately retire and replace any hose assembly exhibiting:\n\n" +
                                "• Blistering, bubbling, or softening of the outer cover (indicates fluid migration past inner tube).\n" +
                                "• Exposed or broken reinforcement wire braid/spirals.\n" +
                                "• Kinks, flattening, or localized bulging along the hose body.\n" +
                                "• Fluid seepage, weeping, or slippage around the crimped metal collar.\n" +
                                "• Cracked, dry-rotted, or weather-checked rubber cover.\n" +
                                "• Corroded or deformed metal fittings."
                    )
                )
            )
        )
    }
}
