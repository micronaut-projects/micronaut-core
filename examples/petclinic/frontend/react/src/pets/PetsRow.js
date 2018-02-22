import React from 'react'
import PetsCell from "./PetsCell";

const PetsRow = ({pets}) => <div className="row">{pets.map(pet => <PetsCell pet={pet} />)}</div>

export default PetsRow