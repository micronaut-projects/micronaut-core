import React from 'react'
import PetsCell from "./PetsCell";

const PetsRow = ({pets}) => <div className="row">{pets.map((pet, i) => <PetsCell key={i} pet={pet} />)}</div>

export default PetsRow